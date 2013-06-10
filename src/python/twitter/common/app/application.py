# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

from __future__ import print_function

__author__ = 'Dave Buchfuhrer, Brian Wickman'

try:
  import ConfigParser
except ImportError:
  import configparser as ConfigParser

import atexit
import copy
import inspect
import optparse
import os
import shlex
import sys
import threading
from collections import defaultdict, deque
from functools import partial

from twitter.common import options
from twitter.common.app.module import AppModule
from twitter.common.app.inspection import Inspection
from twitter.common.lang import Compatibility
from twitter.common.process import daemonize
from twitter.common.util import topological_sort


class Application(object):
  class Error(Exception): pass

  # enforce a quasi-singleton interface (for resettable applications in test)
  _Global = None

  @staticmethod
  def reset():
    """Reset the global application.  Only useful for testing."""
    Application._Global = Application()

  @staticmethod
  def active():
    """Return the current resident application object."""
    return Application._Global

  HELP_OPTIONS = [
    options.Option("-h", "--help", "--short-help",
      action="callback",
      callback=lambda *args, **kwargs: Application.active()._short_help(*args, **kwargs),
      help="show this help message and exit."),
    options.Option("--long-help",
      action="callback",
      callback=lambda *args, **kwargs: Application.active()._long_help(*args, **kwargs),
      help="show options from all registered modules, not just the __main__ module.")
  ]

  IGNORE_RC_FLAG = '--app_ignore_rc_file'

  APP_OPTIONS = {
    'daemonize':
       options.Option('--app_daemonize',
           action='store_true',
           default=False,
           dest='twitter_common_app_daemonize',
           help="Daemonize this application."),

    'daemon_stdout':
       options.Option('--app_daemon_stdout',
           default='/dev/null',
           dest='twitter_common_app_daemon_stdout',
           help="Direct this app's stdout to this file if daemonized."),

    'daemon_stderr':
       options.Option('--app_daemon_stderr',
           default='/dev/null',
           dest='twitter_common_app_daemon_stderr',
           help="Direct this app's stderr to this file if daemonized."),

    'pidfile':
       options.Option('--app_pidfile',
           default=None,
           dest='twitter_common_app_pidfile',
           help="The pidfile to use if --app_daemonize is specified."),

    'debug':
       options.Option('--app_debug',
           action='store_true',
           default=False,
           dest='twitter_common_app_debug',
           help="Print extra debugging information during application initialization."),

    'profiling':
       options.Option('--app_profiling',
           action='store_true',
           default=False,
           dest='twitter_common_app_profiling',
           help="Run profiler on the code while it runs.  Note this can cause slowdowns."),

    'profile_output':
       options.Option('--app_profile_output',
           default=None,
           metavar='FILENAME',
           dest='twitter_common_app_profile_output',
           help="Dump the profiling output to a binary profiling format."),

    'rc_filename':
       options.Option('--app_rc_filename',
           action='store_true',
           default=False,
           dest='twitter_common_app_rc_filename',
           help="Print the filename for the rc file and quit."),

    'ignore_rc_file':
       options.Option(IGNORE_RC_FLAG,
           action='store_true',
           default=False,
           dest='twitter_common_app_ignore_rc_file',
           help="Ignore default arguments from the rc file."),
  }

  NO_COMMAND = 'DEFAULT'
  OPTIONS = 'options'

  OPTIONS_ATTR = '__options__'

  def __init__(self):
    self._name = None
    self._registered_modules = []
    self._init_modules = []
    self._option_targets = defaultdict(dict)
    self._global_options = {}
    self._interspersed_args = False
    self._main_options = Application.HELP_OPTIONS[:]
    self._usage = ""
    self._profiler = None
    self._commands = {}

    self._reset()
    for opt in Application.APP_OPTIONS.values():
      self.add_option(opt)
    self._configure_options(None, Application.APP_OPTIONS)

  def _raise_if_initialized(self, msg="Cannot perform operation after initialization!"):
    if self.initialized:
      raise Application.Error(msg)

  def _raise_if_uninitialized(self, msg="Cannot perform operation before initialization!"):
    if not self.initialized:
      raise Application.Error(msg)

  def _reset(self):
    """
      Resets the state set up by init() so that init() may be called again.
    """
    self.initialized = False
    self._option_values = options.Values()
    self._argv = []

  def interspersed_args(self, value):
    self._interspersed_args = bool(value)

  def _configure_options(self, module, option_dict):
    for opt_name, opt in option_dict.items():
      self._option_targets[module][opt_name] = opt.dest

  def configure(self, module=None, **kw):
    """
      Configure the application object or its activated modules.

      Typically application modules export flags that can be defined on the
      command-line.  In order to allow the application to override defaults,
      these modules may export named parameters to be overridden.  For example,
      the Application object itself exports named variables such as "debug" or
      "profiling", which can be enabled via:
         app.configure(debug=True)
      and
         app.configure(profiling=True)
      respectively.  They can also be enabled with their command-line argument
      counterpart, e.g.
        ./my_application --app_debug --app_profiling

      Some modules export named options, e.g. twitter.common.app.modules.http exports
      'enable', 'host', 'port'.  The command-line arguments still take precedence and
      will override any defaults set by the application in app.configure.  To activate
      these options, just pass along the module name:
        app.configure(module='twitter.common.app.modules.http', enable=True)
    """
    if module not in self._option_targets:
      if not self._import_module(module):
        raise Application.Error('Unknown module to configure: %s' % module)
    def configure_option(name, value):
      if name not in self._option_targets[module]:
        raise Application.Error('Module %s has no option %s' % (module, name))
      self.set_option(self._option_targets[module][name], value)
    for option_name, option_value in kw.items():
      configure_option(option_name, option_value)

  def _main_parser(self):
    return (options.parser()
              .interspersed_arguments(self._interspersed_args)
              .options(self._main_options)
              .usage(self._usage))

  def command_parser(self, command):
    assert command in self._commands
    values_copy = copy.deepcopy(self._option_values)
    parser = self._main_parser()
    command_group = options.new_group(('For %s only' % command) if command else 'Default')
    for option in getattr(self._commands[command], Application.OPTIONS_ATTR):
      op = copy.deepcopy(option)
      if not hasattr(values_copy, op.dest):
        setattr(values_copy, op.dest, op.default if op.default != optparse.NO_DEFAULT else None)
      Application.rewrite_help(op)
      op.default = optparse.NO_DEFAULT
      command_group.add_option(op)
    parser = parser.groups([command_group]).values(values_copy)
    usage = self._commands[command].__doc__
    if usage:
      parser = parser.usage(usage)
    return parser

  def _construct_partial_parser(self):
    """
      Construct an options parser containing only options added by __main__
      or global help options registered by the application.
    """
    if hasattr(self._commands.get(self._command), Application.OPTIONS_ATTR):
      return self.command_parser(self._command)
    else:
      return self._main_parser().values(copy.deepcopy(self._option_values))

  def _construct_full_parser(self):
    """
      Construct an options parser containing both local and global (module-level) options.
    """
    return self._construct_partial_parser().groups(self._global_options.values())

  def _rc_filename(self):
    rc_short_filename = '~/.%src' % self.name()
    return os.path.expanduser(rc_short_filename)

  def _add_default_options(self, argv):
    """
      Return an argument list with options from the rc file prepended.
    """
    rc_filename = self._rc_filename()

    options = argv

    if Application.IGNORE_RC_FLAG not in argv and os.path.exists(rc_filename):
      command = self._command or Application.NO_COMMAND
      rc_config = ConfigParser.SafeConfigParser()
      rc_config.read(rc_filename)

      if rc_config.has_option(command, Application.OPTIONS):
        default_options_str = rc_config.get(command, Application.OPTIONS)
        default_options = shlex.split(default_options_str, True)
        options = default_options + options

    return options

  def _parse_options(self, force_args=None):
    """
      Parse options and set self.option_values and self.argv to the values to be passed into
      the application's main() method.
    """
    argv = sys.argv[1:] if force_args is None else force_args
    if argv and argv[0] in self._commands:
      self._command = argv.pop(0)
    else:
      self._command = None
    parser = self._construct_full_parser()
    self._option_values, self._argv = parser.parse(self._add_default_options(argv))

  def _short_help(self, option, opt, value, parser):
    self._construct_partial_parser().print_help()
    sys.exit(1)

  def _long_help(self, option, opt, value, parser):
    self._construct_full_parser().print_help()
    sys.exit(1)

  def _setup_modules(self):
    """
      Setup all initialized modules.
    """
    module_registry = AppModule.module_registry()
    for bundle in topological_sort(AppModule.module_dependencies()):
      for module_label in bundle:
        assert module_label in module_registry
        module = module_registry[module_label]
        self._debug_log('Initializing: %s (%s)' % (module.label(), module.description()))
        try:
          module.setup_function()
        except AppModule.Unimplemented:
          pass
        self._init_modules.append(module.label())

  def _teardown_modules(self):
    """
      Teardown initialized module in reverse initialization order.
    """
    module_registry = AppModule.module_registry()
    for module_label in reversed(self._init_modules):
      assert module_label in module_registry
      module = module_registry[module_label]
      self._debug_log('Running exit function for %s (%s)' % (module_label, module.description()))
      try:
        module.teardown_function()
      except AppModule.Unimplemented:
        pass

  def _maybe_daemonize(self):
    if self._option_values.twitter_common_app_daemonize:
      daemonize(pidfile=self._option_values.twitter_common_app_pidfile,
                stdout=self._option_values.twitter_common_app_daemon_stdout,
                stderr=self._option_values.twitter_common_app_daemon_stderr)

  # ------- public exported methods -------
  def init(self, force_args=None):
    """
      Initialize the state necessary to run the application's main() function but
      without actually invoking main.  Mostly useful for testing.  If force_args
      specified, use those arguments instead of sys.argv[1:].
    """
    self._raise_if_initialized("init cannot be called twice.  Use reinit if necessary.")
    self._parse_options(force_args)
    self._maybe_daemonize()
    self._setup_modules()
    self.initialized = True

  def reinit(self, force_args=None):
    """
      Reinitialize the application.  This clears the stateful parts of the application
      framework and reruns init().  Mostly useful for testing.
    """
    self._reset()
    self.init(force_args)

  def argv(self):
    self._raise_if_uninitialized("Must call app.init() before you may access argv.")
    return self._argv

  def add_module_path(self, name, path):
    """
      Add all app.Modules defined by name at path.

      Typical usage (e.g. from the __init__.py of something containing many
      app modules):

        app.add_module_path(__name__, __path__)
    """
    import pkgutil
    for _, mod, ispkg in pkgutil.iter_modules(path):
      if ispkg: continue
      fq_module = '.'.join([name, mod])
      __import__(fq_module)
      for (kls_name, kls) in inspect.getmembers(sys.modules[fq_module], inspect.isclass):
        if issubclass(kls, AppModule):
          self.register_module(kls())

  def register_module(self, module):
    """
      Register an app.Module and all its options.
    """
    if not isinstance(module, AppModule):
      raise TypeError('register_module should be called with a subclass of AppModule')
    if module.label() in self._registered_modules:
      # Do not reregister.
      return
    if hasattr(module, 'OPTIONS'):
      if not isinstance(module.OPTIONS, dict):
        raise Application.Error('Registered app.Module %s has invalid OPTIONS.' % module.__module__)
      for opt in module.OPTIONS.values():
        self._add_option(module.__module__, opt)
      self._configure_options(module.label(), module.OPTIONS)
    self._registered_modules.append(module.label())

  @staticmethod
  def _get_module_key(module):
    return 'From module %s' % module

  def _add_main_option(self, option):
    self._main_options.append(option)

  def _add_module_option(self, module, option):
    calling_module = Application._get_module_key(module)
    if calling_module not in self._global_options:
      self._global_options[calling_module] = options.new_group(calling_module)
    self._global_options[calling_module].add_option(option)

  @staticmethod
  def rewrite_help(op):
    if hasattr(op, 'help') and isinstance(op.help, Compatibility.string):
      if op.help.find('%default') != -1 and op.default != optparse.NO_DEFAULT:
        op.help = op.help.replace('%default', str(op.default))
      else:
        op.help = op.help + ((' [default: %s]' % str(op.default))
          if op.default != optparse.NO_DEFAULT else '')

  def _add_option(self, calling_module, option):
    op = copy.deepcopy(option)
    if op.dest and hasattr(op, 'default'):
      self.set_option(op.dest, op.default if op.default != optparse.NO_DEFAULT else None,
        force=False)
      Application.rewrite_help(op)
      op.default = optparse.NO_DEFAULT
    if calling_module == '__main__':
      self._add_main_option(op)
    else:
      self._add_module_option(calling_module, op)

  def _get_option_from_args(self, args, kwargs):
    if len(args) == 1 and kwargs == {} and isinstance(args[0], options.Option):
      return args[0]
    else:
      return options.TwitterOption(*args, **kwargs)


  def add_option(self, *args, **kwargs):
    """
      Add an option to the application.

      You may pass either an Option object from the optparse/options module, or
      pass the *args/**kwargs necessary to construct an Option.
    """
    self._raise_if_initialized("Cannot call add_option() after main()!")
    calling_module = Inspection.find_calling_module()
    added_option = self._get_option_from_args(args, kwargs)
    self._add_option(calling_module, added_option)

  def command(self, function=None, name=None):
    """
      Decorator to turn a function into an application command.

      To add a command foo, the following patterns will both work:

      @app.command
      def foo(args, options):
        ...

      @app.command(name='foo')
      def bar(args, options):
        ...
    """
    if name is None:
      return self._register_command(function)
    else:
      return partial(self._register_command, command_name=name)

  def _register_command(self, function, command_name=None):
    """
      Registers function as the handler for command_name. Uses function.__name__ if command_name
      is None.
    """
    if Inspection.find_calling_module() == '__main__':
      if command_name is None:
        command_name = function.__name__
      if command_name in self._commands:
        raise Application.Error('Found two definitions for command %s' % command_name)
      self._commands[command_name] = function
    return function

  def default_command(self, function):
    """
      Decorator to make a command default.
    """
    if Inspection.find_calling_module() == '__main__':
      if None in self._commands:
        defaults = (self._commands[None].__name__, function.__name__)
        raise Application.Error('Found two default commands: %s and %s' % defaults)
      self._commands[None] = function
    return function

  def command_option(self, *args, **kwargs):
    """
      Decorator to add an option only for a specific command.
    """
    def register_option(function):
      added_option = self._get_option_from_args(args, kwargs)
      if not hasattr(function, Application.OPTIONS_ATTR):
        setattr(function, Application.OPTIONS_ATTR, deque())
      getattr(function, Application.OPTIONS_ATTR).appendleft(added_option)
      return function
    return register_option

  def copy_command_options(self, command_function):
    """
      Decorator to copy command options from another command.
    """
    def register_options(function):
      if hasattr(command_function, Application.OPTIONS_ATTR):
        if not hasattr(function, Application.OPTIONS_ATTR):
          setattr(function, Application.OPTIONS_ATTR, deque())
        command_options = getattr(command_function, Application.OPTIONS_ATTR)
        getattr(function, Application.OPTIONS_ATTR).extendleft(command_options)
      return function
    return register_options

  def add_command_options(self, command_function):
    """
      Function to add all options from a command
    """
    module = inspect.getmodule(command_function).__name__
    for option in getattr(command_function, Application.OPTIONS_ATTR, []):
      self._add_option(module, option)

  def _debug_log(self, msg):
    if hasattr(self._option_values, 'twitter_common_app_debug') and (
        self._option_values.twitter_common_app_debug):
      print('twitter.common.app debug: %s' % msg, file=sys.stderr)

  def set_option(self, dest, value, force=True):
    """
      Set a global option value either pre- or post-initialization.

      If force=False, do not set the default if already overridden by a manual call to
      set_option.
    """
    if hasattr(self._option_values, dest) and not force:
      return
    setattr(self._option_values, dest, value)

  def get_options(self):
    """
      Return all application options, both registered by __main__ and all imported modules.
    """
    return self._option_values

  def get_commands(self):
    """
      Return all valid commands registered by __main__
    """
    return filter(None, self._commands.keys())

  def get_commands_and_docstrings(self):
    """
      Generate all valid commands together with their docstrings
    """
    for command, function in self._commands.items():
      if command is not None:
        yield command, function.__doc__

  def get_local_options(self):
    """
      Return the options only defined by __main__.
    """
    new_values = options.Values()
    for opt in self._main_options:
      if opt.dest:
        setattr(new_values, opt.dest, getattr(self._option_values, opt.dest))
    return new_values

  def set_usage(self, usage):
    """
      Set the usage message should the user call --help or invalidly specify options.
    """
    self._usage = usage

  def error(self, message):
    """
      Print the application help message, an error message, then exit.
    """
    self._construct_partial_parser().error(message)

  def help(self):
    """
      Print the application help message and exit.
    """
    self._short_help(*(None,)*4)

  def set_name(self, application_name):
    """
      Set the application name.  (Autodetect otherwise.)
    """
    self._raise_if_initialized("Cannot set application name.")
    self._name = application_name

  def name(self):
    """
      Return the name of the application.  If set_name was never explicitly called,
      the application framework will attempt to autodetect the name of the application
      based upon the location of __main__.
    """
    if self._name is not None:
      return self._name
    else:
      try:
        return Inspection.find_application_name()
      except:
        return 'unknown'

  def quit(self, rc, exit_function=sys.exit):
    self._debug_log('Shutting application down.')
    self._teardown_modules()
    self._debug_log('Finishing up module teardown.')
    nondaemons = 0
    self.dump_profile()
    for thr in threading.enumerate():
      self._debug_log('  Active thread%s: %s' % (' (daemon)' if thr.isDaemon() else '', thr))
      if thr is not threading.current_thread() and not thr.isDaemon():
        nondaemons += 1
    if nondaemons:
      self._debug_log('More than one active non-daemon thread, your application may hang!')
    else:
      self._debug_log('Exiting cleanly.')
    exit_function(rc)

  def profiler(self):
    if self._option_values.twitter_common_app_profiling:
      if self._profiler is None:
        try:
          import cProfile as profile
        except ImportError:
          import profile
        self._profiler = profile.Profile()
      return self._profiler
    else:
      return None

  def dump_profile(self):
    if self._option_values.twitter_common_app_profiling:
      if self._option_values.twitter_common_app_profile_output:
        self.profiler().dump_stats(self._option_values.twitter_common_app_profile_output)
      else:
        self.profiler().print_stats(sort='time')

  def _run_main(self, main_method, *args, **kwargs):
    try:
      if self.profiler():
        rc = self.profiler().runcall(main_method, *args, **kwargs)
      else:
        rc = main_method(*args, **kwargs)
    except SystemExit as e:
      rc = e.code
      self._debug_log('main_method exited with return code = %s' % repr(rc))
    except KeyboardInterrupt as e:
      rc = None
      self._debug_log('main_method exited with ^C')
    return rc

  def _import_module(self, name):
    """
      Import the module, return True on success, False if the import failed.
    """
    try:
      __import__(name)
      return True
    except ImportError:
      return False

  def main(self):
    """
      If called from __main__ module, run script's main() method with arguments passed
      and global options parsed.

      The following patterns are acceptable for the main method:
         main()
         main(args)
         main(args, options)
    """
    main_module = Inspection.find_calling_module()
    if main_module != '__main__':
      # only support if __name__ == '__main__'
      return

    # Pull in modules in twitter.common.app.modules
    if not self._import_module('twitter.common.app.modules'):
      print('Unable to import twitter app modules!', file=sys.stderr)
      sys.exit(1)

    # defer init as long as possible.
    self.init()

    if self._option_values.twitter_common_app_rc_filename:
      print('RC filename: %s' % self._rc_filename())
      return

    try:
      caller_main = Inspection.find_main_from_caller()
    except Inspection.InternalError:
      caller_main = None
    if None in self._commands:
      assert caller_main is None, "Error: Cannot define both main and a default command."
    else:
      self._commands[None] = caller_main
    main_method = self._commands[self._command]
    if main_method is None:
      commands = sorted(self.get_commands())
      if commands:
        print('Must supply one of the following commands:', ', '.join(commands), file=sys.stderr)
      else:
        print('No main() or command defined! Application must define one of these.', file=sys.stderr)
      sys.exit(1)

    try:
      argspec = inspect.getargspec(main_method)
    except TypeError as e:
      print('Malformed main(): %s' % e, file=sys.stderr)
      sys.exit(1)

    if len(argspec.args) == 1:
      args = [self._argv]
    elif len(argspec.args) == 2:
      args = [self._argv, self._option_values]
    else:
      if len(self._argv) != 0:
        print('main() takes no arguments but got leftover arguments: %s!' %
          ' '.join(self._argv), file=sys.stderr)
        sys.exit(1)
      args = []
    rc = self._run_main(main_method, *args)
    self.quit(rc)
