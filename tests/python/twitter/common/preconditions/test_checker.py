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

import unittest

from contextlib import contextmanager

import pytest

from twitter.common.preconditions.checker import (
    ArgChecker,
    Checker,
    CheckError)


@contextmanager
def expected_check_error():
  with pytest.raises(CheckError) as e:
    yield e


class CheckTest(unittest.TestCase):
  def test_usage(self):
    def check_sum(sum, *param_names):
      def correct_sum(call_info):
        total = 0
        for param_name in param_names:
          total += call_info.call_args[param_name]
        if total != sum:
          raise CheckError('Total of argument values was %d, expected %d; '
                           'args were %r' % (total, sum, call_info.call_args))
      return Checker().add_check(correct_sum)

    @check_sum(100, 'first', 'third')
    def redistribute(first, second, third):
      return (first + third) * second

    self.assertEqual(100000, redistribute(1, 1000, 99))

    with expected_check_error():
      redistribute(1000, 1, 99)

  def test_improper_usage(self):
    # extra checks must be 1 arg callables
    chk = Checker()

    with pytest.raises(ValueError):
      chk.add_check(1)

    bad_zero_arg = lambda: None
    with pytest.raises(ValueError):
      chk.add_check(bad_zero_arg)

    bad_multi_arg = lambda x, y: None
    with pytest.raises(ValueError):
      chk.add_check(bad_multi_arg)

    # decorated item must be a function or method
    with pytest.raises(ValueError):
      @Checker()
      class Foo(object):
        pass

    with pytest.raises(ValueError):
      Checker()(42)

  def test_disable(self):
    def doomed_by_foo():
      pass

    doomed_by_foo = Checker().add_check(lambda _: 'foo')(doomed_by_foo)
    with expected_check_error() as e:
      doomed_by_foo()
    self.assertEqual('foo', str(e.value))

    Checker.disable()
    try:
      def doomed_by_foo_but_disabled():
        return 'phew?'
      doomed_by_foo_but_disabled = Checker().add_check(lambda _: 'foo')(doomed_by_foo_but_disabled)
      self.assertEqual('phew?', doomed_by_foo_but_disabled())
    finally:
      Checker.enable()

  def test_one_of_usage(self):
    noop_checked_identity = Checker.one_of()(lambda x: x)
    self.assertEqual(42, noop_checked_identity(42))

    just_check_none = Checker.one_of(ArgChecker('x', required=True))(lambda x: x)
    self.assertEqual(42, just_check_none(42))

    with expected_check_error():
      just_check_none(None)

    def int_or_string(name1, name2):
      return Checker.one_of(ArgChecker(name1, int), ArgChecker(name2, str))

    @int_or_string('bob', 'bill')
    def check_decoration_checks(bob, bill):
      return bob, bill

    # 1st check fails, second succeeds
    a, b = check_decoration_checks(1L, 'a')
    self.assertEqual(1L, a)
    self.assertEqual('a', b)

    # 1st check succeeds
    a, b = check_decoration_checks(1, [])
    self.assertEqual(1, a)
    self.assertEqual([], b)

  def test_one_of_improper_usage(self):
    # disjunction is only over Checker objects
    with pytest.raises(ValueError):
      Checker.one_of(ArgChecker('a', required=True), 13)

    def int_or_string(name1, name2):
      return Checker.one_of(ArgChecker(name1, int), ArgChecker(name2, str))

    with pytest.raises(KeyError):
      @int_or_string('george', 'bill')
      def bad_name1(bob, bill):
        pass

    with pytest.raises(KeyError):
      @int_or_string('bob', 'george')
      def bad_name2(bob, bill):
        pass


class ArgCheckTest(unittest.TestCase):
  def test_usage(self):
    @ArgChecker('a', frozenset)
    @ArgChecker('b')
    @ArgChecker('c', required=True)
    def checked(a, b, c):
      return a, b, c

    a_value = frozenset([1, 2])
    a, b, c = checked(a_value, 'not None', 'not None')
    self.assertTrue(a is a_value)
    self.assertEqual('not None', b)
    self.assertEqual('not None', c)

    with expected_check_error():
      checked(set([1, 2]), 42, 1137)

    with expected_check_error():
      checked(frozenset([1, 2]), None, 1137)

    with expected_check_error():
      checked(frozenset([1, 2]), 42, None)

    def small_frozenset(name, required=True):
      return ArgChecker(name, frozenset, required=required).add_check(lambda value: len(value) < 3)

    @small_frozenset('a')
    def checked_small(a):
      return len(a)

    value = frozenset([1, 2])
    self.assertEqual(2, checked_small(value))
    with expected_check_error():
      checked_small(set([1, 2, 3]))

  def test_improper_usage(self):
    with pytest.raises(ValueError):
      ArgChecker(1, bool)  # name must be a string

    with pytest.raises(ValueError):
      ArgChecker('fred', 1)  # types must be types

    with pytest.raises(ValueError):
      ArgChecker('fred', bool, required=1)  # required must be a bool

    with pytest.raises(KeyError):  # name must match a parameter name in the decorated function
      @ArgChecker('baz', int)
      def foo(bar):
        return bar

  def test_partial(self):
    @ArgChecker('first', bool)
    @ArgChecker('third', int, required=False)
    def check_partial(first, second='a', third=42):
      return first, second, third

    a, b, c = check_partial(True)
    self.assertEqual(True, a)
    self.assertEqual('a', b)
    self.assertEqual(42, c)

    a, b, c = check_partial(False, [], None)
    self.assertEqual(False, a)
    self.assertEqual([], b)
    self.assertEqual(None, c)

    with expected_check_error():
      check_partial(1)

    with expected_check_error():
      check_partial(None)

    with expected_check_error():
      check_partial(False, third=3.0)

  def test_varargs(self):
    @ArgChecker('first', allowed_types=tuple)
    def check_varargs(*first):
      return first

    self.assertEqual((), check_varargs())
    self.assertEqual((1, 2, 3), check_varargs(1, 2, 3))

    @ArgChecker('first', allowed_types=int)
    def func(*first):
      return first

    with expected_check_error():
      func()

  def test_kwargs(self):
    @ArgChecker('first', allowed_types=dict)
    def check_kwargs(**first):
      return first

    self.assertEqual({}, check_kwargs())
    self.assertEqual(dict(fred='bob'), check_kwargs(fred='bob'))

    @ArgChecker('first', allowed_types=str)
    def func(**first):
      return first

    with expected_check_error():
      func()

  def test_positional_varargs_kwargs(self):
    @ArgChecker('second', allowed_types=tuple)
    @ArgChecker('third', allowed_types=dict)
    def check_positional_varargs_kwargs(first, *second, **third):
      return first, second, third

    a, b, c = check_positional_varargs_kwargs(42)
    self.assertEqual(42, a)
    self.assertEqual((), b)
    self.assertEqual({}, c)

    a, b, c = check_positional_varargs_kwargs('jake', 1, 2, 3)
    self.assertEqual('jake', a)
    self.assertEqual((1, 2, 3), b)
    self.assertEqual({}, c)

    a, b, c = check_positional_varargs_kwargs('joe', fred='bob', jane=42)
    self.assertEqual('joe', a)
    self.assertEqual((), b)
    self.assertEqual(dict(fred='bob', jane=42), c)

    @ArgChecker('second', allowed_types=int)
    @ArgChecker('third', allowed_types=dict)
    def bad_varargs(first, *second, **third):
      return first, second, third

    with expected_check_error():
      bad_varargs(1)

    @ArgChecker('second', allowed_types=tuple)
    @ArgChecker('third', allowed_types=int)
    def bad_kwargs(first, *second, **third):
      return first, second, third

    with expected_check_error():
      bad_kwargs(1)
