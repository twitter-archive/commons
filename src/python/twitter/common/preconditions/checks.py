# ==================================================================================================
# Copyright 2014 Twitter, Inc.
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

import collections
import os
import re

from functools import partial

from twitter.common.collections import maybe_list
from twitter.common.lang import Compatibility
from twitter.common.preconditions.checker import ArgChecker, Checker, type_check


def check_not_none(name):
  """A precondition check that the named argument is passed a non ``None`` value."""
  return ArgChecker(name)


def check_type(allowed_types, name, required=True):
  """A precondition check that the named argument is passed a valid value.

  See :class:`ArgChecker` for semantics - this is just a shuffling of the order of its constructor
  arguments for convenient currying of the ``allowed_types`` to form type-specific checks.
  """
  return ArgChecker(name, allowed_types, required)


check_bool = partial(check_type, bool)
check_integral = partial(check_type, Compatibility.integer)
check_int = partial(check_type, int)
check_long = partial(check_type, long)
check_float = partial(check_type, float)


def check_string(name, non_empty=False, regex=None, required=True):
  """A precondition check for string arguments.

  :param string name: The parameter name to check.
  :param bool non_empty: If ``True`` the string must contain at least one character
  :param string regex: An optional regex the string must match
  :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
      allowed to take a value of ``None``)
  """
  chk = check_type(Compatibility.string, name, required)
  if non_empty:
    def check_non_empty(value):
      if len(value) == 0:
        return 'Param `%s` cannot be empty, given %r' % (name, value)
    chk.add_check(check_non_empty)
  if regex:
    pattern = re.compile(regex)

    def check_regex(value):
      if not pattern.match(value):
        return 'Param `%s`=%r doe snot match regex: %r' % (name, value, regex)
    chk.add_check(check_regex)
  return chk


def check_iterable(name, types=None, non_empty=False, required=True):
  """A precondition check for iterable arguments.

  :param string name: The parameter name to check.
  :param types: An optional type or sequence of types that the values produced by the iterable must
      conform to.
  :param bool non_empty: If ``True`` the iterable must contain at least one value
  :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
      allowed to take a value of ``None``)
  """
  chk = check_type(collections.Iterable, name, required)
  if non_empty:
    def check_non_empty(values):
      try:
        iter(values).next()
      except StopIteration:
        return 'Iterable `%s` must contain at least on value, given %r' % (name, values)
    chk.add_check(check_non_empty)
  if types:
    types = tuple(maybe_list(types, expected_type=type))

    def check_values(values):
      for index, value in enumerate(values):
        if not isinstance(value, types):
          return ('Items in iterable `%s` are expected to be of types %s but item %d is %r of '
                  'type %s' % (name, types, index, value, type(value)))
    chk.add_check(check_values)
  return chk


def check_list(name, types=None, non_empty=False, required=True):
  """A precondition check for arguments that are (mutable) sequence types.

  :param string name: The parameter name to check.
  :param types: An optional type or sequence of types that the values in the sequence must
      conform to.
  :param bool non_empty: If ``True`` the iterable must contain at least one value
  :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
      allowed to take a value of ``None``)
  """
  return check_iterable(name, types, non_empty, required).add_check(
      type_check(collections.MutableSequence, name))


def check_set(name, types=None, non_empty=False, required=True):
  """A precondition check for arguments that are (mutable) set types.

  :param string name: The parameter name to check.
  :param types: An optional type or sequence of types that the values in the set must conform to.
  :param bool non_empty: If ``True`` the iterable must contain at least one value
  :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
      allowed to take a value of ``None``)
  """
  return check_iterable(name, types, non_empty, required).add_check(
      type_check(collections.MutableSet, name))


def check_mapping(name, key_types=None, value_types=None, non_empty=False, required=True):
  """A precondition check for arguments that are (mutable) mapping types.

  :param string name: The parameter name to check.
  :param key_types: An optional type or sequence of types that the keys in the mapping must
      conform to.
  :param key_types: An optional type or sequence of types that the values in the mapping must
      conform to.
  :param bool non_empty: If ``True`` the iterable must contain at least one value
  :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
      allowed to take a value of ``None``)
  """
  chk = check_iterable(name, key_types, non_empty, required).add_check(
      type_check(collections.MutableMapping, name))

  if value_types:
    value_types = tuple(maybe_list(value_types, expected_type=type))

    def check_values(mapping):
      for key, value in mapping.items():
        if not isinstance(value, value_types):
          return ('Values in mapping `%s` are expected to be of types %s but value keyed by %r is '
                  '%r of type %s' % (name, value_types, key, value, type(value)))
    chk.add_check(check_values)
  return chk


def check_maybe_list(name, types=Compatibility.string, required=True):
  """A precondition check for a single argument of the given types or else an iterable containing
  items of those types.

  This is meant to work in conjunction with parameters converted to lists with the ``maybe_list``
  utility function.

  :param string name: The parameter name to check.
  :param types: An optional type or sequence of types that the values in the set must conform to.
  :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
      allowed to take a value of ``None``)
  """
  check_one = check_type(types, name, required)
  check_many = check_iterable(name, types, required)
  return Checker.one_of(check_one, check_many)


def check_path(name, exists=False, required=True):
  """A precondition check for for valid paths.

  :param string name: The parameter name to check.
  :param exists: If ``True`` the path must exist; otherwise the path must just be a string.
  :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
      allowed to take a value of ``None``)
  """
  chk = check_string(name, required)
  if exists:
    def check_exists(value):
      if not os.path.exists(value):
        return 'Path parameter `%s`=%r but that path does not exist.' % (name, value)
    chk.add_check(check_exists)
  return chk


def check_file_path(name, required=True):
  """A precondition check for for valid file paths.

  The file path must point to an existing file.

  :param string name: The parameter name to check.
  :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
      allowed to take a value of ``None``)
  """
  def check_file(value):
    if os.path.isdir(value):
      return 'File path parameter `%s`=%r but that path is not a file.' % (name, value)
  return check_path(name, exists=True, required=required).add_check(check_file)


def check_directory(name, required=True):
  """A precondition check for for valid directory paths.

  The directory path must point to an existing directory.

  :param string name: The parameter name to check.
  :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
      allowed to take a value of ``None``)
  """
  def check_dir(value):
    if not os.path.isdir(value):
      return 'Directory path parameter `%s`=%r but that path is not a directory.' % (name, value)
  return check_path(name, exists=True, required=required).add_check(check_dir)
