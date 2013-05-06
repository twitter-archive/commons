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
  Twitter's wrapper around the optparse module for doing more stateless builder-style
  options parsing.

  Typical usage:

    from twitter.common import options

    base_parser = options.parser()
    my_opts = [
      options.Option(...),
      options.Option(...)
    ]
    my_foo_opts = [
      options.Option(...),
      options.Option(...)
    ]
    group = base_parser.new_group('foo')
    group.add_option(*my_foo_opts)
    parser = base_parser
             .options(my_opts)
             .groups([group])
             .interspersed_arguments(True)
             .usage("blah blah blah"))
    values, rargs = parser.parse()
"""

__author__ = 'Brian Wickman'

import copy
import sys
import inspect
import types

from optparse import (
  OptionParser,
  OptionValueError,
  Option,
  OptionGroup,
  Values,
  NO_DEFAULT
)

from .twitter_option import TwitterOption

def parser():
  return TwitterOptionParser()

def new_group(name):
  return TwitterOptionGroup(name)

group = new_group

__all__ = [
  'parser',
  'new_group',
  'group', # alias for new_group
  'Option',
  'TwitterOption',
  'Values'
]

class TwitterOptionGroup(object):
  def __init__(self, name):
    self._name = name
    self._option_list = []

  def add_option(self, *option):
    self._option_list.extend(option)

  def prepend_option(self, *option):
    self._option_list = list(option) + self._option_list

  def options(self):
    return self._option_list

  def name(self):
    return self._name

  @staticmethod
  def format_help(group, header=None):
    pass

class TwitterOptionParser(object):
  """
    Wrapper for builder-style stateless options parsing.
  """

  class InvalidParameters(Exception): pass
  class InvalidArgument(Exception): pass

  ATTRS = [ '_interspersed_arguments', '_usage', '_options', '_groups', '_values' ]

  def __init__(self):
    self._interspersed_arguments = False
    self._usage = ""
    self._options = []
    self._groups = []
    self._values = Values()

  def interspersed_arguments(self, i_a=None):
    """ Enable/disable interspersed arguments. """
    if i_a is None:
      return self._interspersed_arguments
    me = self._copy()
    me._interspersed_arguments = i_a
    return me

  def usage(self, new_usage=None):
    """ Get/set usage. """
    if new_usage is None:
      return self._usage
    me = self._copy()
    me._usage = new_usage
    return me

  def options(self, merge_options=None):
    """ Get/add options. """
    if merge_options is None:
      return self._options
    me = self._copy()
    me._options.extend(merge_options)
    return me

  def groups(self, merge_groups=None):
    """ Get/add groups. """
    if merge_groups is None:
      return self._groups
    me = self._copy()
    me._groups.extend(merge_groups)
    return me

  def values(self, merge_values=None):
    """ Get/update default/parsed values. """
    if merge_values is None:
      return self._values
    me = self._copy()
    TwitterOptionParser._merge_values(me._values, merge_values)
    return me

  @staticmethod
  def _merge_values(values1, values2):
    for attr in values2.__dict__:
      if getattr(values2, attr) != NO_DEFAULT:
        setattr(values1, attr, getattr(values2, attr))

  def _copy(self):
    c = TwitterOptionParser()
    for attr in TwitterOptionParser.ATTRS:
      setattr(c, attr, copy.deepcopy(getattr(self, attr)))
    return c

  def _init_parser(self):
    parser = OptionParser(add_help_option=False, usage=self.usage())
    parser.allow_interspersed_args = self.interspersed_arguments()
    for op in self.options():
      parser.add_option(copy.deepcopy(op))
    for gr in self.groups():
      real_group = parser.add_option_group(gr.name())
      for op in gr.options():
        real_group.add_option(copy.deepcopy(op))
    return parser

  # There is enough special-casing that we're doing to muck with the optparse
  # module that it might be worthwhile in writing our own, sigh.
  def parse(self, argv=None):
    """ Parse argv.  If argv=None, use sys.argv[1:]. """
    parser = self._init_parser()
    inherit_values = copy.deepcopy(self.values())
    if isinstance(inherit_values, dict):
      inherit_values = Values(inherit_values)
    if argv is None:
      argv = sys.argv[1:]
    values, leftover = parser.parse_args(args=argv)
    for attr in copy.copy(values.__dict__):
      if getattr(values, attr) is None:
        delattr(values, attr)
    TwitterOptionParser._merge_values(inherit_values, values)
    return inherit_values, leftover

  def print_help(self):
    parser = self._init_parser()
    parser.print_help()

  def error(self, message):
    parser = self._init_parser()
    parser.error(message)

  def __enter__(self):
    return self

  def __exit__(self, *args):
    return False
