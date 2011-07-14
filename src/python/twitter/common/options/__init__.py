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

import inspect

from optparse import OptionParser, OptionValueError

class OptionsHaveNotBeenParsedException(Exception): pass
class OptionsAreFrozenException(Exception): pass
class OptionsInternalErrorException(Exception): pass

class _Global:
  OPTIONS = OptionParser()
  VALUES = None
  GROUPS = {}
  PARSED = False

  @staticmethod
  def assert_not_parsed(msg=""):
    if _Global.PARSED:
      raise OptionsAreFrozenException(msg)

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
  else:
    adding_module = 'From module %s' % adding_module
    if adding_module not in _Global.GROUPS:
      _Global.GROUPS[adding_module] = _Global.OPTIONS.add_option_group(adding_module)
    _Global.GROUPS[adding_module].add_option(*args, **kwargs)

# alias
add_option = add

def parse(*args, **kwargs):
  _Global.assert_not_parsed("Called options.parse() twice!")
  _Global.VALUES, left = _Global.OPTIONS.parse_args(*args, **kwargs)
  _Global.PARSED = True
  return _Global.VALUES, left

# alias
parse_args = parse

def values():
  if not _Global.PARSED:
    raise OptionsHaveNotBeenParsedException("Must call options.parse() from __main__ module!")
  return _Global.VALUES

def set_usage(usage):
  _Global.assert_not_parsed()
  _Global.OPTIONS.set_usage(usage)

def help(*args, **kwargs):
  _Global.OPTIONS.print_help(*args, **kwargs)

# alias
print_help = help

__all__ = [
  'add',
  'add_option',
  'parse',
  'parse_args',
  'values',
  'set_usage',
  'help',
  'print_help',
  'OptionsHaveNotBeenParsedException',
  'OptionsAreFrozenException',
  'OptionsInternalErrorException',
  'OptionValueError',
]
