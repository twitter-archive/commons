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

import json
import pkgutil
import unittest

from twitter.thrift.text import thrift_json_decoder
from gen.twitter.thrift.text.testing import ttypes as structs_for_testing


class ThriftJsonDecoderTest(unittest.TestCase):
  def test_decode_json_to_thrift(self):
    json_str = \
"""{
  "field1": 7,
  "field2": true,
  "field3": "Hello, World",
  "field4": [
    2,
    4,
    6,
    8
  ],
  "field5": [
    "yes",
    "maybe",
    "no"
  ],
  "field6": {
    "foo": "bar",
    "color": 3,
    "numbers": {
      "1": "one",
      "2": "two",
      "3": "three"
    }
  },
  "field7": 1.2
}"""

    res = thrift_json_decoder.json_to_thrift(json_str, structs_for_testing.TestStruct)
    assert res.field1 == 7
    assert res.field2 == True
    assert res.field3 == 'Hello, World'
    assert res.field4 == [2, 4, 6, 8]
    assert res.field5 == set(['no', 'yes', 'maybe'])
    assert res.field6.foo == 'bar'
    assert res.field6.color == 3
    assert res.field6.numbers == { 1: 'one', 2: 'two', 3: 'three' }
    assert res.field7 == 1.2
