"""
  Basic Twitter Python application framework.

  Example usage:
    from twitter.common import app
    from twitter.common import options, log
    options.add('--my_option', dest='my_option', help="my commandline argument!")

    def main(args):
      log.info('my options argument is: %s' % option_values.my_option)
      log.info('my argv is: %s' % args)

    app.main()

  app.main() replaces the "if __name__ == '__main__': main()" idiom and runs any
  initialization code by app-compatible libraries as many in twitter.common are.
"""

import sys
import runpy
import types
import inspect

from environments import Environment
from inspection import Inspection

# This is the one thing that is provided de-facto with every application
# whether you like it or not.
from twitter.common import options

class AppException(Exception): pass

options.add(
 '--env', '--environment',
 action='callback',
 callback=Environment._option_parser,
 default='DEVELOPMENT',
 metavar='ENV',
 dest='twitter_common_app_environment',
 help="The environment in which to run this Python application. "
 "Known environments: %s [default: %%default]" % ' '.join(Environment.names()))

options.add(
 '--app_debug',
 action='store_true',
 default=False,
 dest='twitter_common_app_debug',
 help="Print extra debugging information during application initialization.")

_APP_REGISTRY = {}
_APP_NAME = None
_APP_INITIALIZED = False

__all__ = [
  # exceptions
  'AppException',

  # Registry mutation
  'on_initialization',

  # StartMethods
  'main',
  'init',

  # Application introspection
  'name',
  'environment'
]

def on_initialization(function,
                      environment=None,
                      runlevel=0,
                      description=""):
  """
    Run function on initialization of the application.  (Or run immediately, once,
    if the application has already been initialized.)

    Parameters:
      function:
        The function to run.
      environment (optional of type app.Environment):
        If specified, only run this function when running the supplied
        environment.  If None, always run.
      runlevel (optional of type Integer):
        Used to supply a temporal ordering of when to run initialization
        functions.  Default: 0.
      description (optional string):
        Description of this initialization function, useful for debugging.
  """
  if type(runlevel) is not types.IntType:
    raise AppException("Invalid runlevel: %s, must be an integer." % runlevel)
  if type(function) is not types.FunctionType:
    raise AppException("Supplied function does not appear to be callable: %s" % function)
  if type(environment) is not types.NoneType and not isinstance(environment, Environment):
    raise AppException(
      "Supplied environment must be None or of type app.Environment: %s" % environment)

  if runlevel not in _APP_REGISTRY:
    _APP_REGISTRY[runlevel] = []
  _APP_REGISTRY[runlevel].append((function, environment, description))

  # Must call if the application has already been initialized.
  if _APP_INITIALIZED:
    function()

class Methods(object):
  @staticmethod
  def _environment():
    if not _APP_INITIALIZED:
      raise AppException("Cannot get environment until twitter.common.app has been initialized!")
    return options.values().twitter_common_app_environment

  @staticmethod
  def _run_registry():
    global _APP_INITIALIZED
    if _APP_INITIALIZED:
      raise AppException("Attempted to initialize application more than once!")
    # initialize options.
    options.parse()
    environment = options.values().twitter_common_app_environment
    for runlevel in sorted(_APP_REGISTRY.keys()):
      for fn, env, description in _APP_REGISTRY[runlevel]:
        if env is None or environment == env:
          if options.values().twitter_common_app_debug and description:
            print >> sys.stderr, \
              "twitter.common.app runlevel %s, running initializer: %s" % (
                runlevel, description)
          fn()
    _APP_INITIALIZED = True

  @staticmethod
  def _main():
    """
      If called from __main__ module, run script's main() method.
    """
    main_method, main_module = Inspection._find_main_from_caller(), Inspection._find_main_module()
    if main_module != '__main__':
      # only support if __name__ == '__main__'
      return
    if main_method is None:
      print >> sys.stderr, 'No main() defined!  Application must define main function.'
      sys.exit(1)
    Methods._run_registry()

    try:
      argspec = inspect.getargspec(main_method)
    except TypeError, e:
      print >> sys.stderr, 'Malformed main(): %s' % e
      sys.exit(1)

    if len(argspec.args) == 1 or argspec.varargs is not None:
      # def main(foo), main(foo, *args) or main(*args) ==> take arguments
      main_method(options.arguments())
    else:
      # def main() or def main(**kwargs)
      if len(options.arguments()) != 0:
        print >> sys.stderr, 'main() takes no arguments but got leftover arguments %s!' % (
          ' '.join(options.arguments()))
        sys.exit(1)
      main_method()

  @staticmethod
  def _init():
    """
      Initialize twitter.common.app without calling main().  This is not particularly
      advisable in practice, but could come in handy for debugging.
    """
    Methods._run_registry()

main = Methods._main
init = Methods._init
environment = Methods._environment

def name():
  if _APP_NAME is not None:
    return _APP_NAME
  else:
    return Inspection._find_main_module()
