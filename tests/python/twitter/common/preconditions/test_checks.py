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

import os
import pytest
import unittest

from contextlib import contextmanager

from twitter.common.collections import maybe_list
from twitter.common.contextutil import temporary_dir
from twitter.common.dirutil import touch

from twitter.common.preconditions.checker import CheckError
from twitter.common.preconditions.checks import (
    check_bool,
    check_directory,
    check_file_path,
    check_int,
    check_iterable,
    check_mapping,
    check_maybe_list,
    check_not_none,
    check_path,
    check_string,
    check_type)


@contextmanager
def expected_check_error():
  with pytest.raises(CheckError) as e:
    yield e


class ChecksTest(unittest.TestCase):
  def test_not_none(self):
    @check_not_none('second')
    def func(first, second):
      return first, second

    self.assertEqual((1, 2), func(1, 2))
    self.assertEqual((None, 'a'), func(None, 'a'))

    with expected_check_error():
      func(1, None)

  def test_check_type(self):
    @check_type((CheckError, int), 'arg')
    def func(arg):
      return arg

    error = CheckError()
    self.assertEqual(error, func(error))

    class Sub(CheckError):
      pass
    sub = Sub()
    self.assertEqual(sub, func(sub))

    with expected_check_error():
      func(42L)

    with expected_check_error():
      func(ValueError())

  def test_bool(self):
    @check_bool('arg')
    def func(arg=False):
      return arg

    self.assertEqual(False, func())
    self.assertEqual(True, func(True))

    with expected_check_error():
      func(1)

  def test_string(self):
    @check_string('notes')
    def record(notes):
      return notes

    self.assertEqual('', record(''))
    self.assertEqual('testing strings.', record('testing strings.'))

    with expected_check_error():
      record(03045)

    @check_string('zipcode', non_empty=True)
    def county(zipcode):
      return zipcode

    self.assertEqual('03045', county('03045'))

    with expected_check_error():
      county('')

    @check_string('email_address', regex='[a-z]+@[^@]+')
    def subscribe(email_address):
      return email_address

    self.assertEqual('jack@jill.com', subscribe('jack@jill.com'))

    with expected_check_error():
      subscribe('@jill.com')

  def test_iterable(self):
    @check_iterable('first')
    @check_iterable('second', types=(int, long))
    @check_iterable('third', types=None, non_empty=True)
    def func(first, second, third):
      return first, second, third

    a, b, c = func((), (), ('a',))
    self.assertEqual((), a)
    self.assertEqual((), b)
    self.assertEqual(('a',), c)

    a, b, c = func('', [1137L, 42], (True,))
    self.assertEqual('', a)
    self.assertEqual([1137L, 42], b)
    self.assertEqual((True,), c)

    with expected_check_error():
      func(1, (), ['a'])

    with expected_check_error():
      func((), (1, 'a'), ['a'])

    with expected_check_error():
      func((), (1, 2), ())

  def test_mapping(self):
    @check_mapping('first')
    @check_mapping('second', key_types=(int, long))
    @check_mapping('third', value_types=int)
    @check_mapping('fourth', non_empty=True)
    def func(first, second, third, **fourth):
      return first, second, third, fourth

    a, b, c, d = func({}, {}, {}, jake=42)
    self.assertEqual({}, a)
    self.assertEqual({}, b)
    self.assertEqual({}, c)
    self.assertEqual(dict(jake=42), d)

    a, b, c, d = func({'a': 1}, {42: 'a', 42L: 'b'}, {(1, 2): 42}, jake=42)
    self.assertEqual({'a': 1}, a)
    self.assertEqual({42: 'a', 42L: 'b'}, b)
    self.assertEqual({(1, 2): 42}, c)
    self.assertEqual(dict(jake=42), d)

    with expected_check_error():
      func([], {42: 'a', 42L: 'b'}, {(1, 2): 42}, jake=42)

    with expected_check_error():
      func({'a': 1}, {42.0: 'a'}, {(1, 2): 42}, jake=42)

    with expected_check_error():
      func({'a': 1}, {42: 'a', 42L: 'b'}, {(1, 2): '42'}, jake=42)

    with expected_check_error():
      func({'a': 1}, {42: 'a', 42L: 'b'}, {(1, 2): 42})

  def test_maybe_list(self):
    @check_maybe_list('items', types=(int, str), required=False)
    @check_int('size')
    def one_or_more(items, size):
      return maybe_list(items or (), expected_type=(int, str)), size

    self.assertEqual(([], 0), one_or_more(None, 0))
    self.assertEqual(([], 0), one_or_more((), 0))
    self.assertEqual(([42], 1), one_or_more(42, 1))
    self.assertEqual(([42, 'jumpstreet'], 2), one_or_more((42, 'jumpstreet'), 2))

    with expected_check_error():
      one_or_more(42L, 1)

    with expected_check_error():
      one_or_more(42, 'a')

    def check_maybe_list_small_ints(name):
      def check_small_ints(value):
        values = maybe_list(value, expected_type=int)
        for idx, x in enumerate(values):
          if x > 9:
            return 'Values must be less than 10; got item index %d of %d=%r' % (idx, len(values), x)
      return check_maybe_list(name, types=int).add_check(check_small_ints)

    @check_maybe_list_small_ints('items')
    def one_or_more_small_ints(items):
      return maybe_list(items, expected_type=int)

    self.assertEqual([1], one_or_more_small_ints(1))
    self.assertEqual([3, 2], one_or_more_small_ints([3, 2]))

    # Additional checks should trigger along either branch
    with expected_check_error():
      one_or_more_small_ints(10)
    with expected_check_error():
      one_or_more_small_ints((1, 2, 10, 4, 5))

  def test_path(self):
    @check_path('arg')
    def func_path(arg):
      return arg

    @check_path('arg', exists=True)
    def func_path_exists(arg):
      return arg

    with temporary_dir() as chroot:
      self.assertTrue(func_path_exists(chroot) is chroot)

      path = os.path.join(chroot, 'fred')
      self.assertTrue(func_path(path) is path)

      with expected_check_error():
        func_path_exists(path)

      touch(path)
      self.assertTrue(func_path_exists(path) is path)

  def test_file_path(self):
    @check_file_path('arg')
    def func_file_path(arg):
      return arg

    with temporary_dir() as chroot:
      with expected_check_error():
        func_file_path(chroot)

      path = os.path.join(chroot, 'fred')
      touch(path)
      self.assertTrue(func_file_path(path) is path)

  def test_directory(self):
    @check_directory('arg')
    def func_dir_path(arg):
      return arg

    with temporary_dir() as chroot:
      self.assertTrue(func_dir_path(chroot) is chroot)

      path = os.path.join(chroot, 'fred')
      touch(path)

      with expected_check_error():
        func_dir_path(path)
