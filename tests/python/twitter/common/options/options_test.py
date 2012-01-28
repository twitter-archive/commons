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

from twitter.common import options

import pytest
import unittest

class TestOptions(unittest.TestCase):
  def test_parser_defaults(self):
    parser = options.parser()
    assert parser.interspersed_arguments() is False
    assert parser.usage() == ""

  def test_mutation_creates_new_parser(self):
    parser1 = options.parser()
    parser2 = parser1.interspersed_arguments(True)
    parser3 = parser2.usage("foo")
    assert parser1 is not parser2
    assert parser2 is not parser3
    assert parser1 is not parser3
    assert parser1.interspersed_arguments() is False
    assert parser1.usage() == ""
    assert parser2.interspersed_arguments() is True
    assert parser2.usage() == ""
    assert parser3.interspersed_arguments() is True
    assert parser3.usage() == "foo"

  def test_basic_parsing(self):
    option = options.Option('-m', '--my_option', dest='my_option')

    # w/o option
    values, leftovers = options.parser().options([option]).parse([])
    assert not hasattr(values, 'my_option')
    assert leftovers == []

    # w/ option
    values, leftovers = options.parser().options([option]).parse(['-m', 'poop'])
    assert values.my_option == 'poop'
    assert leftovers == []

    # w/ long option
    values, leftovers = options.parser().options([option]).parse(['--my_option', 'plork'])
    assert values.my_option == 'plork'
    assert leftovers == []

    # w/ option and leftover
    values, leftovers = options.parser().options([option]).parse(['--my_option', 'plork', 'hork'])
    assert values.my_option == 'plork'
    assert leftovers == ['hork']

  def test_default_parsing(self):
    option = options.Option('-m', '--my_option', default="specified", dest='my_option')
    values, leftovers = options.parser().options([option]).parse([])
    assert hasattr(values, 'my_option')
    assert leftovers == []
    assert values.my_option == 'specified'

  def test_value_inheritance(self):
    option_list = [
      options.Option('-a', dest='a'),
      options.Option('-b', dest='b')
    ]

    values, leftovers = options.parser().options(option_list).parse([])
    assert not hasattr(values, 'a')
    assert not hasattr(values, 'b')

    # w/ option
    values, leftovers = options.parser().options(option_list).parse(['-a', 'value_a'])
    assert hasattr(values, 'a')
    assert values.a == 'value_a'
    assert not hasattr(values, 'b')

    # w/ inherited option
    values, leftovers = options.parser().values(values).options(option_list).parse(['-b', 'value_b'])
    assert values.a == 'value_a'
    assert values.b == 'value_b'

    # w/ inherits w/o parsing any new args
    values, leftovers = options.parser().values(values).options(option_list).parse([])
    assert values.a == 'value_a'
    assert values.b == 'value_b'

    # w/ overwrites despite inheriting
    values, leftovers = options.parser().values(values).options(option_list).parse(['-a', 'new_value_a'])
    assert values.a == 'new_value_a'
    assert values.b == 'value_b'

  def test_multiple_value_inheritance(self):
    option_list = [
      options.Option('-a', dest='a'),
      options.Option('-b', dest='b')
    ]

    values_with_a, _ = options.parser().options(option_list).parse(['-a', 'value_a'])
    values_with_b, _ = options.parser().options(option_list).parse(['-b', 'value_b'])
    values, leftovers = (options.parser()
                                .options(option_list)
                                .values(values_with_a)
                                .values(values_with_b)).parse([])
    assert values.a == 'value_a'
    assert values.b == 'value_b'

    # and parsed values overwrite
    values, leftovers = (options.parser()
                                .options(option_list)
                                .values(values_with_a)
                                .values(values_with_b)).parse(['-a', 'new_value_a'])
    assert values.a == 'new_value_a'
    assert values.b == 'value_b'

  def test_multiple_option_inheritance(self):
    option_a = options.Option('-a', dest='a')
    option_b = options.Option('-b', dest='b')
    values, leftovers = (options.parser()
                                .options([option_a])
                                .options([option_b])).parse(['-a', 'value_a', '-b', 'value_b'])
    assert values.a == 'value_a'
    assert values.b == 'value_b'

  def test_groups(self):
    option_a = options.Option('-a', dest='a')
    option_b = options.Option('-b', dest='b')
    option_group_a = options.group('a')
    option_group_b = options.group('b')
    option_group_a.add_option(options.Option('--a1', dest='a1'), options.Option('--a2', dest='a2'))
    option_group_b.add_option(options.Option('--b1', dest='b1'), options.Option('--b2', dest='b2'))

    partial_parser = (options.parser()
                             .interspersed_arguments(True)
                             .groups([option_group_a, option_group_b]))
    full_parser = partial_parser.options([option_a, option_b])

    parameters = ['--a1', 'value_a1', '--a2', 'value_a2',
                  '--b1', 'value_b1', '--b2', 'value_b2']
    full_parameters = parameters + ['-a', 'value_a', '-b', 'value_b']

    values, leftovers = partial_parser.parse(parameters)
    assert values.a1 == 'value_a1'
    assert values.a2 == 'value_a2'
    assert values.b1 == 'value_b1'
    assert values.b2 == 'value_b2'
    assert leftovers == []

    values, leftovers = full_parser.parse(full_parameters)
    assert values.a1 == 'value_a1'
    assert values.a2 == 'value_a2'
    assert values.b1 == 'value_b1'
    assert values.b2 == 'value_b2'
    assert values.a == 'value_a'
    assert values.b == 'value_b'
    assert leftovers == []
