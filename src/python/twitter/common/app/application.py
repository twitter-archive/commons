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

try:
  import ConfigParser
except ImportError:
  import configparser as ConfigParser

from collections import defaultdict, deque
import copy
from functools import partial, wraps
import inspect
import optparse
import os
import shlex
import signal
import sys
import threading
import time
import traceback

from twitter.common import options
from twitter.common.lang import Compatibility
from twitter.common.process import daemonize
from twitter.common.util import topological_sort

from .inspection import Inspection
from .module import AppModule


class Application(object):
  class Error(Exception): pass

  # enforce a quasi-singleton interface (for resettable applications in test)
  _GLOBAL = None

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

  OPTIONS = 'options'
  OPTIONS_ATTR = '__options__'
  NO_COMMAND = 'DEFAULT'
  SIGINT_RETURN_CODE = 130  # see http://tldp.org/LDP/abs/html/exitcodes.html

  INITIALIZING = 1
  INITIALIZED = 2
  RUNNING = 3
  ABORTING = 4
  SHUTDOWN = 5

  @classmethod
  def reset(cls):
    """Reset the global application.  Only useful for testing."""
    cls._GLOBAL = cls()

  @classmethod
  def active(cls):
    """Return the current resident application object."""
    return cls._GLOBAL

  def __init__(self, exit_function=sys.exit, force_args=None):
    self._name = None
    self._exit_function = exit_function
    self._force_args = force_args
    self._registered_modules = []
    self._init_modules = []
    self._option_targets = defaultdict(dict)
    self._global_options = {}
    self._interspersed_args = False
    self._main_options = self.HELP_OPTIONS[:]
    self._main_thread = None
    self._shutdown_commands = []
    self._usage = ""
    self._profiler = None
    self._commands = {}
    self._state = self.INITIALIZING

    self._reset()
    for opt in self.APP_OPTIONS.values():
      self.add_option(opt)
    self._configure_options(None, self.APP_OPTIONS)

  def pre_initialization(method):
    @wraps(method)
    def wrapped_method(self, *args, **kw):
      if self._state > self.INITIALIZING:
        raise self.Error("Cannot perform operation after initialization!")
      return method(self, *args, **kw)
    return wrapped_method

  def post_initialization(method):
    @wraps(method)
    def wrapped_method(self, *args, **kw):
      if self._state < self.INITIALIZED:
        raise self.Error("Cannot perform operation before initialization!")
      return method(self, *args, **kw)
    return wrapped_method

  def _reset(self):
    """
      Resets the state set up by init() so that init() may be called again.
    """
    self._state = self.INITIALIZING
    self._option_values = options.Values()
    self._argv = []

  def interspersed_args(self, value):
    self._interspersed_args = bool(value)

  def _configure_options(self, module, option_dict):
    for opt_name, opt in option_dict.items():
      self._option_targets[module][opt_name] = opt.dest

  @pre_initialization
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
        raise self.Error('Unknown module to configure: %s' % module)
    def configure_option(name, value):
      if name not in self._option_targets[module]:
        raise self.Error('Module %s has no option %s' % (module, name))
      self.set_option(self._option_targets[module][name], value)
    for option_name, option_value in kw.items():
      configure_option(option_name, option_value)

  def _main_parser(self):
    return (options.parser().interspersed_arguments(self._interspersed_args)
                            .options(self._main_options)
                            .usage(self._usage))

  def command_parser(self, command):
    assert command in self._commands
    values_copy = copy.deepcopy(self._option_values)
    parser = self._main_parser()
    command_group = options.new_group(('For %s only' % command) if command else 'Default')
    for option in getattr(self._commands[command], Application.OPTIONS_ATTR, []):
      op = copy.deepcopy(option)
      if not hasattr(values_copy, op.dest):
        setattr(values_copy, op.dest, op.default if op.default != optparse.NO_DEFAULT else None)
      self.rewrite_help(op)
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
    if hasattr(self._commands.get(self._command), self.OPTIONS_ATTR):
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

    if self.IGNORE_RC_FLAG not in argv and os.path.exists(rc_filename):
      command = self._command or self.NO_COMMAND
      rc_config = ConfigParser.SafeConfigParser()
      rc_config.read(rc_filename)

      if rc_config.has_option(command, self.OPTIONS):
        default_options_str = rc_config.get(command, self.OPTIONS)
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
    self._exit_function(1)
    return

  def _long_help(self, option, opt, value, parser):
    self._construct_full_parser().print_help()
    self._exit_function(1)
    return

  @pre_initialization
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
    if self._state != self.SHUTDOWN:
      raise self.Error('Expected application to be in SHUTDOWN state!')
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
  @pre_initialization
  def init(self):
    """
      Initialize the state necessary to run the application's main() function but
      without actually invoking main.
    """
    self._parse_options(self._force_args)
    self._maybe_daemonize()
    self._setup_modules()
    self._state = self.INITIALIZED

  def reinit(self, force_args=None):
    """
      Reinitialize the application.  This clears the stateful parts of the application
      framework and reruns init().  Mostly useful for testing.
    """
    self._reset()
    self.init(force_args)

  @post_initialization
  def argv(self):
    return self._argv

  @pre_initialization
  def add_module_path(self, name, path):
    """
      Add all app.Modules defined by name at path.

      Typical usage (e.g. from the __init__.py of something containing many
      app modules):

        app.add_module_path(__name__, __path__)
    """
    import pkgutil
    for _, mod, ispkg in pkgutil.iter_modules(path):
      if ispkg:
        continue
      fq_module = '.'.join([name, mod])
      __import__(fq_module)
      for (kls_name, kls) in inspect.getmembers(sys.modules[fq_module], inspect.isclass):
        if issubclass(kls, AppModule):
          self.register_module(kls())

  @pre_initialization
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
        raise self.Error('Registered app.Module %s has invalid OPTIONS.' % module.__module__)
      for opt in module.OPTIONS.values():
        self._add_option(module.__module__, opt)
      self._configure_options(module.label(), module.OPTIONS)
    self._registered_modules.append(module.label())

  @classmethod
  def _get_module_key(cls, module):
    return 'From module %s' % module

  @pre_initialization
  def _add_main_option(self, option):
    self._main_options.append(option)

  @pre_initialization
  def _add_module_option(self, module, option):
    calling_module = self._get_module_key(module)
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
      self.rewrite_help(op)
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

  @pre_initialization
  def add_option(self, *args, **kwargs):
    """
      Add an option to the application.

      You may pass either an Option object from the optparse/options module, or
      pass the *args/**kwargs necessary to construct an Option.
    """
    calling_module = Inspection.find_calling_module()
    added_option = self._get_option_from_args(args, kwargs)
    self._add_option(calling_module, added_option)

  def _set_command_origin(self, function, command_name):
    function.__app_command_origin__ = (self, command_name)

  def _get_command_name(self, function):
    assert self._is_app_command(function)
    return function.__app_command_origin__[1]

  def _is_app_command(self, function):
    return callable(function) and (
        getattr(function, '__app_command_origin__', (None, None))[0] == self)

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
      return self._command(function)
    else:
      return partial(self._command, name=name)

  def _command(self, function, name=None):
    command_name = name or function.__name__
    self._set_command_origin(function, command_name)
    if Inspection.find_calling_module() == '__main__':
      self._register_command(function, command_name)
    return function

  def register_commands_from(self, *modules):
    """
      Given an imported module, walk the module for commands that have been
      annotated with @app.command and register them against this
      application.
    """
    for module in modules:
      for _, function in inspect.getmembers(module, predicate=lambda fn: callable(fn)):
        if self._is_app_command(function):
          self._register_command(function, self._get_command_name(function))

  @pre_initialization
  def _register_command(self, function, command_name):
    """
      Registers function as the handler for command_name. Uses function.__name__ if command_name
      is None.
    """
    if command_name in self._commands:
      raise self.Error('Found two definitions for command %s' % command_name)
    self._commands[command_name] = function
    return function

  def default_command(self, function):
    """
      Decorator to make a command default.
    """
    if Inspection.find_calling_module() == '__main__':
      if None in self._commands:
        defaults = (self._commands[None].__name__, function.__name__)
        raise self.Error('Found two default commands: %s and %s' % defaults)
      self._commands[None] = function
    return function

  @pre_initialization
  def command_option(self, *args, **kwargs):
    """
      Decorator to add an option only for a specific command.
    """
    def register_option(function):
      added_option = self._get_option_from_args(args, kwargs)
      if not hasattr(function, self.OPTIONS_ATTR):
        setattr(function, self.OPTIONS_ATTR, deque())
      getattr(function, self.OPTIONS_ATTR).appendleft(added_option)
      return function
    return register_option

  @pre_initialization
  def copy_command_options(self, command_function):
    """
      Decorator to copy command options from another command.
    """
    def register_options(function):
      if hasattr(command_function, self.OPTIONS_ATTR):
        if not hasattr(function, self.OPTIONS_ATTR):
          setattr(function, self.OPTIONS_ATTR, deque())
        command_options = getattr(command_function, self.OPTIONS_ATTR)
        getattr(function, self.OPTIONS_ATTR).extendleft(command_options)
      return function
    return register_options

  def add_command_options(self, command_function):
    """
      Function to add all options from a command
    """
    module = inspect.getmodule(command_function).__name__
    for option in getattr(command_function, self.OPTIONS_ATTR, ()):
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
    return list(filter(None, self._commands.keys()))

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

  @pre_initialization
  def set_usage(self, usage):
    """
      Set the usage message should the user call --help or invalidly specify options.
    """
    self._usage = usage

  def set_usage_based_on_commands(self):
    """
      Sets the usage message automatically, to show the available commands.
    """
    self.set_usage(
      'Please run with one of the following commands:\n' +
      '\n'.join(['  %-22s%s' % (command, self._set_string_margin(docstring or '', 0, 24))
                 for (command, docstring) in self.get_commands_and_docstrings()])
    )

  @staticmethod
  def _set_string_margin(s, first_line_indentation, other_lines_indentation):
    """
      Given a multi-line string, resets the indentation to the given number of spaces.
    """
    lines = s.strip().splitlines()
    lines = ([' ' * first_line_indentation  + line.strip() for line in lines[:1]] +
             [' ' * other_lines_indentation + line.strip() for line in lines[1:]])
    return '\n'.join(lines)

  def error(self, message):
    """
      Print the application help message, an error message, then exit.
    """
    self._construct_partial_parser().error(message)

  def help(self):
    """
      Print the application help message and exit.
    """
    self._short_help(None, None, None, None)

  @pre_initialization
  def set_name(self, application_name):
    """
      Set the application name.  (Autodetect otherwise.)
    """
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
      # TODO(wickman) Be more specific
      except Exception:
        return 'unknown'

  def quit(self, return_code):
    nondaemons = 0
    for thr in threading.enumerate():
      self._debug_log('  Active thread%s: %s' % (' (daemon)' if thr.isDaemon() else '', thr))
      if thr is not threading.current_thread() and not thr.isDaemon():
        nondaemons += 1
    if nondaemons:
      self._debug_log('More than one active non-daemon thread, your application may hang!')
    else:
      self._debug_log('Exiting cleanly.')
    self._exit_function(return_code)

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

  # The thread module provides the interrupt_main() function which does
  # precisely what it says, sending a KeyboardInterrupt to MainThread.  The
  # only problem is that it only delivers the exception while the MainThread
  # is running.  If one does time.sleep(10000000) it will simply block
  # forever.  Sending an actual SIGINT seems to be the only way around this.
  # Of course, applications can trap SIGINT and prevent the quitquitquit
  # handlers from working.
  #
  # Furthermore, the following cannot work:
  #
  # def main():
  #   shutdown_event = threading.Event()
  #   app.register_shutdown_command(lambda rc: shutdown_event.set())
  #   shutdown_event.wait()
  #
  # because threading.Event.wait() is uninterruptible.  This is why
  # abortabortabort is so severe.  An application that traps SIGTERM will
  # render the framework unable to abort it, so SIGKILL is really the only
  # way to be sure to force termination because it cannot be trapped.
  #
  # For the particular case where the bulk of the work is taking place in
  # background threads, use app.wait_forever().
  def quitquitquit(self):
    self._state = self.ABORTING
    os.kill(os.getpid(), signal.SIGINT)

  def abortabortabort(self):
    self._state = self.SHUTDOWN
    os.kill(os.getpid(), signal.SIGKILL)

  def register_shutdown_command(self, command):
    if not callable(command):
      raise TypeError('Shutdown command must be a callable.')
    if self._state >= self.ABORTING:
      raise self.Error('Cannot register a shutdown command while shutting down.')
    self._shutdown_commands.append(command)

  def _wrap_method(self, method, method_name=None):
    method_name = method_name or method.__name__
    try:
      return_code = method()
    except SystemExit as e:
      self._debug_log('%s sys.exited' % method_name)
      return_code = e.code
    except KeyboardInterrupt as e:
      if self._state >= self.ABORTING:
        self._debug_log('%s being shutdown' % method_name)
        return_code = 0
      else:
        self._debug_log('%s exited with ^C' % method_name)
        return_code = self.SIGINT_RETURN_CODE
    except Exception as e:
      return_code = 1
      self._debug_log('%s excepted with %s' % (method_name, type(e)))
      sys.excepthook(*sys.exc_info())
    return return_code

  @post_initialization
  def _run_main(self, main_method, *args, **kwargs):
    if self.profiler():
      main = lambda: self.profiler().runcall(main_method, *args, **kwargs)
    else:
      main = lambda: main_method(*args, **kwargs)

    self._state = self.RUNNING
    return self._wrap_method(main, method_name='main')

  def _run_shutdown_commands(self, return_code):
    while self._state != self.SHUTDOWN and self._shutdown_commands:
      command = self._shutdown_commands.pop(0)
      command(return_code)

  def _run_module_teardown(self):
    if self._state != self.SHUTDOWN:
      raise self.Error('Expected application to be in SHUTDOWN state!')
    self._debug_log('Shutting application down.')
    self._teardown_modules()
    self._debug_log('Finishing up module teardown.')
    self.dump_profile()

  def _import_module(self, name):
    """
      Import the module, return True on success, False if the import failed.
    """
    try:
      __import__(name)
      return True
    except ImportError:
      return False

  def _validate_main_module(self):
    main_module = Inspection.find_calling_module()
    return main_module == '__main__'

  def _default_command_is_defined(self):
    return None in self._commands

  # Allow for overrides in test
  def _find_main_method(self):
    try:
      return Inspection.find_main_from_caller()
    except Inspection.InternalError:
      pass

  def _get_main_method(self):
    caller_main = self._find_main_method()
    if self._default_command_is_defined() and caller_main is not None:
      print('Error: Cannot define both main and a default command.', file=sys.stderr)
      self._exit_function(1)
      return
    main_method = self._commands.get(self._command) or caller_main
    if main_method is None:
      commands = sorted(self.get_commands())
      if commands:
        print('Must supply one of the following commands:', ', '.join(commands), file=sys.stderr)
      else:
        print('No main() or command defined! Application must define one of these.',
            file=sys.stderr)
    return main_method

  def wait_forever(self):
    """Convenience function to block the application until it is terminated
       by ^C or lifecycle functions."""
    while True:
      time.sleep(0.5)

  def shutdown(self, return_code):
    self._wrap_method(lambda: self._run_shutdown_commands(return_code),
         method_name='shutdown commands')
    self._state = self.SHUTDOWN
    self._run_module_teardown()
    self.quit(return_code)

  def main(self):
    """
      If called from __main__ module, run script's main() method with arguments passed
      and global options parsed.

      The following patterns are acceptable for the main method:
         main()
         main(args)
         main(args, options)
    """
    if not self._validate_main_module():
      # only support if __name__ == '__main__'
      return

    # Pull in modules in twitter.common.app.modules
    if not self._import_module('twitter.common.app.modules'):
      print('Unable to import twitter app modules!', file=sys.stderr)
      self._exit_function(1)
      return

    # defer init as long as possible.
    self.init()

    if self._option_values.twitter_common_app_rc_filename:
      print('RC filename: %s' % self._rc_filename())
      return

    main_method = self._get_main_method()
    if main_method is None:
      self._exit_function(1)
      return

    try:
      argspec = inspect.getargspec(main_method)
    except TypeError as e:
      print('Malformed main(): %s' % e, file=sys.stderr)
      self._exit_function(1)
      return

    if len(argspec.args) == 1:
      args = [self._argv]
    elif len(argspec.args) == 2:
      args = [self._argv, self._option_values]
    else:
      if len(self._argv) != 0:
        print('main() takes no arguments but got leftover arguments: %s!' %
          ' '.join(self._argv), file=sys.stderr)
        self._exit_function(1)
        return
      args = []

    self.shutdown(self._run_main(main_method, *args))

  del post_initialization
  del pre_initialization
