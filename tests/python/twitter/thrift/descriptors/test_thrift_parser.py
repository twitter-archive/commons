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

import pkgutil
import pytest
import unittest
import os.path

from twitter.thrift.descriptors.thrift_parser import ThriftParser
from twitter.thrift.descriptors.thrift_parser_error import ThriftParserError
from twitter.thrift.text import thrift_json_encoder

def pytest_funcarg__generate_golden_data(request):
  """py.test magic for passing the --generate_golden_data flag=/path/to/golden/data to the test."""
  return request.config.option.generate_golden_data

@pytest.mark.xfail(reason="pytest 2.6")
def test_thrift_parser(generate_golden_data):
  """Tests that we can parse a complex file that tickles as many cases and corner cases
  as we can think of. We verify the result against golden data."""
  TEST_DATA_FILE = 'test_data/test_data.thrift'
  GOLDEN_DATA_FILE = TEST_DATA_FILE + '.golden'
  TEST_DATA_PATH = __name__

  test_data = pkgutil.get_data(TEST_DATA_PATH, TEST_DATA_FILE)
  golden_data = pkgutil.get_data(TEST_DATA_PATH, GOLDEN_DATA_FILE)
  parser = ThriftParser()
  print 'Parsing file %s...' % TEST_DATA_FILE,
  program = parser.parse_string(test_data)
  print 'OK.'
  res = thrift_json_encoder.thrift_to_json(program)

  if golden_data is not None:
    # Generate new golden data to the specified path. Use this only once you're
    # convinced that the generated data is correct and the old golden data is not.
    if generate_golden_data is not None:
      with open(generate_golden_data, 'w') as fd:
        fd.write(res)

    assert golden_data == res

@pytest.mark.xfail(reason="pytest 2.6")
def test_parse_various_files():
  """Tests that we can parse, without choking, test files that are part of the original
  thrift parser's test suite. We just check that parsing succeeds, and don't verify the
  results."""
  TEST_DATA_FILES = [
    "AnnotationTest.thrift", "ConstantsDemo.thrift", "DenseLinkingTest.thrift",
    "OptionalRequiredTest.thrift", "StressTest.thrift", "DebugProtoTest.thrift",
    "DocTest.thrift", "ManyTypedefs.thrift", "SmallTest.thrift", "ThriftTest.thrift"
  ]
  TEST_DATA_DIR = 'test_data'
  TEST_DATA_PATH = __name__

  parser = ThriftParser()
  for test_data_file in TEST_DATA_FILES:
    test_data = pkgutil.get_data(TEST_DATA_PATH, os.path.join(TEST_DATA_DIR, test_data_file))
    print 'Parsing file %s...' % test_data_file,
    program = parser.parse_string(test_data)
    print 'OK.'


def _parse_with_expected_error(test_data, expected_error_msg):
  parser = ThriftParser()
  with pytest.raises(ThriftParserError) as exception_info:
    program = parser.parse_string(test_data)
  assert str(exception_info.value) == expected_error_msg


def test_repeated_type_alias():
  test_data = """
    typedef i32 Foo
    typedef string Foo
  """
  _parse_with_expected_error(test_data, 'line 3, col 19: Type alias already exists: Foo')


def test_invalid_enum_value():
  test_data = """
    enum Foo {
      BAR = -1
      BAZ = 1
    }
  """
  _parse_with_expected_error(test_data, 'line 3, col 6: Enum value for BAR must be >= 0: -1')

  test_data = """
    enum Foo {
      BAR = 1
      BAZ = 2147483648
    }
  """
  _parse_with_expected_error(test_data,
                             'line 4, col 6: Enum value for BAZ must be < 2^31: 2147483648')

  test_data = """
    enum Foo {
      BAR = 3
      BAZ = 3
    }
  """
  _parse_with_expected_error(test_data, 'line 4, col 6: Enum value for BAZ must be >= 4: 3')


def test_invalid_field_identifier():
  test_data = """
    struct Foo {
      0: i32 foo
    }
  """
  _parse_with_expected_error(test_data, 'line 3, col 6: Field identifier for foo must be >= 1: 0')

  test_data = """
    struct Foo {
      32768: i32 foo
    }
  """
  _parse_with_expected_error(test_data,
                             'line 3, col 6: Field identifier for foo must be < 2^15: 32768')

  test_data = """
    struct Foo {
      123: required i32 foo,
      123: optional string bar
    }
  """
  _parse_with_expected_error(test_data,
                             'line 4, col 6: Field identifier 123 for bar already used for foo')


def test_repeated_field_name():
  test_data = """
    struct Foo {
      1: required i32 foo,
      2: optional string foo
    }
  """
  _parse_with_expected_error(test_data, 'line 4, col 25: Field name foo for identifier 2' +
                                        ' already used for identifier 1')

def test_repeated_function_name():
  test_data = """
    service Foo {
      void foo(),
      bool foo(1:string bar)
    }
  """
  _parse_with_expected_error(test_data,
                             'line 4, col 11: Function name foo already used in this service')
