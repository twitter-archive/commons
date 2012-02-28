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

import json
import pkgutil
import unittest

from twitter.thrift.text import thrift_json_encoder
from gen.twitter.thrift.text.testing import ttypes as structs_for_testing


class ThriftJsonEncoderTest(unittest.TestCase):
  def test_encode_thrift_to_json(self):
    x = structs_for_testing.TestStruct()
    x.field2 = True
    x.field4 = [2, 4, 6, 8]
    x.field7 = 1.2

    expected1 = \
"""{
  "field2": true,
  "field4": [
    2,
    4,
    6,
    8
  ],
  "field7": 1.2
}"""

    json_str1 = thrift_json_encoder.thrift_to_json(x)
    assert expected1 == json_str1

    x.field1 = 42
    x.field2 = False
    x.field3 = '"not default"'
    x.field4.append(10)
    x.field5 = set(['b', 'c', 'a'])
    x.field6 = structs_for_testing.InnerTestStruct()
    x.field6.foo = "bar"
    x.field6.color =  structs_for_testing.Color.BLUE

    expected2 = \
"""{
  "field1": 42,
  "field2": false,
  "field3": "\\"not default\\"",
  "field4": [
    2,
    4,
    6,
    8,
    10
  ],
  "field5": [
    "a",
    "b",
    "c"
  ],
  "field6": {
    "color": 3,
    "foo": "bar"
  },
  "field7": 1.2
}"""

    json_str2 = thrift_json_encoder.thrift_to_json(x)
    print(json_str2)
    assert expected2 == json_str2

