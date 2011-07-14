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

"""
  Twitter's wrapper around the optparse module.

  Typical usage (from module):
    from twitter.common import options
    options.add("-q", "--quiet",
                action="store_false", dest="verbose", default=True,
                help="don't print status messages to stdout")

    def my_function():
      opts = options.values()
      if opts.verbose:
        print 'LOOOOOOLLOLOLOL'

  From __main__ script:
    from twitter.common import options
    options.add(...)
    options.add(...)

    def main(argv):
      options.parse()

  All options are stored in a global options registry, and are grouped by
  the module from which they were added (and displayed as such in --help.)

  options.parse() must be called before modules can use options.values().
  options.values() will raise an exception if called before options.parse()
  by the __main__ module.  this is to prevent parsing before all options
  have been registered.

  For more help on options formatting, see the optparse module.
"""

__author__ = 'Brian Wickman'

import sys
import inspect
import types

from optparse import OptionParser, OptionValueError

class OptionsHaveNotBeenParsedException(Exception): pass
class OptionsAreFrozenException(Exception): pass
class OptionsInternalErrorException(Exception): pass

class _Global:
  OPTIONS = OptionParser(add_help_option=False)
  OPTIONS_LONG = OptionParser(add_help_option=False)
  VALUES = None
  OVERRIDES = {}
  ARGUMENTS = None
  GROUPS = {}
  PARSED = False

  @staticmethod
  def assert_not_parsed(msg=""):
    if _Global.PARSED:
      raise OptionsAreFrozenException(msg)

  @staticmethod
  def warn_if_parsed(msg=""):
    if _Global.PARSED:
      print >> sys.stderr, 'Warning: calling options.parse multiple times!'

def _short_help(option, opt, value, parser):
  _Global.OPTIONS.print_help()

def _long_help(option, opt, value, parser):
  _Global.OPTIONS_LONG.print_help()

def _add_both_options(*args, **kwargs):
  _Global.OPTIONS.add_option(*args, **kwargs)
  _Global.OPTIONS_LONG.add_option(*args, **kwargs)

_add_both_options(
  "-h", "--help", "--short-help",
  action="callback",
  callback=_short_help,
  help="show this help message and exit.")

_add_both_options(
  "--long-help",
  action="callback",
  callback=_long_help,
  help="show options from all registered modules, not just the __main__ module.")

def _find_calling_module():
  stack = inspect.stack()
  for fr_n in range(len(stack)):
    if '__name__' in stack[fr_n][0].f_locals:
      return stack[fr_n][0].f_locals['__name__']
  raise OptionsInternalErrorException("Unable to interpret stack frame from logging module.")

def add(*args, **kwargs):
  _Global.assert_not_parsed("Cannot add new options after option.parse() has been called!")
  adding_module = _find_calling_module()
  if adding_module == '__main__':
    _Global.OPTIONS.add_option(*args, **kwargs)
    _Global.OPTIONS_LONG.add_option(*args, **kwargs)
  else:
    adding_module = 'From module %s' % adding_module
    if adding_module not in _Global.GROUPS:
      _Global.GROUPS[adding_module] = _Global.OPTIONS_LONG.add_option_group(adding_module)
    _Global.GROUPS[adding_module].add_option(*args, **kwargs)

# alias
add_option = add

def parse(args=None):
  _Global.warn_if_parsed()

  args_to_parse = []
  if args is None:
    args_to_parse.extend(sys.argv[1:])
  args_to_parse.extend('--%s=%s' % (k,v) for (k,v) in _Global.OVERRIDES.items())
  _Global.VALUES, _Global.ARGUMENTS = _Global.OPTIONS_LONG.parse_args(
    args=args_to_parse, values=_Global.VALUES)
  _Global.PARSED = True
  return _Global.VALUES, _Global.ARGUMENTS

# alias
parse_args = parse

def values():
  if not _Global.PARSED:
    raise OptionsHaveNotBeenParsedException("options.parse() has not been called!")
  return _Global.VALUES

def arguments():
  if not _Global.PARSED:
    raise OptionsHaveNotBeenParsedException("options.parse() has not been called!")
  return _Global.ARGUMENTS

def set_option(option_name, option_value):
  if isinstance(option_value, types.StringTypes) and isinstance(option_name, types.StringTypes):
    if option_name in _Global.OVERRIDES:
      print >> sys.stderr, "WARNING: Calling set_option(%s) multiple times!" % option_name
    _Global.OVERRIDES[option_name] = option_value
  else:
    raise OptionValueError("set_option values must be strings!")

def set_usage(usage):
  _Global.assert_not_parsed()
  _Global.OPTIONS.set_usage(usage)
  _Global.OPTIONS_LONG.set_usage(usage)

def help(*args, **kwargs):
  _Global.OPTIONS.print_help(*args, **kwargs)

def longhelp(*args, **kwargs):
  _Global.OPTIONS_LONG.print_help(*args, **kwargs)

# alias
print_help = help

__all__ = [
  'add',
  'add_option',
  'parse',
  'parse_args',
  'values',
  'arguments',
  'set_option',
  'set_usage',
  'help',
  'longhelp',
  'print_help',
  'OptionsHaveNotBeenParsedException',
  'OptionsAreFrozenException',
  'OptionsInternalErrorException',
  'OptionValueError',
]
