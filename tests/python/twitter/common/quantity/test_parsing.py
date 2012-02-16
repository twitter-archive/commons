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

import pytest
from twitter.common.quantity import Time, Amount
from twitter.common.quantity.parse_simple import parse_time, InvalidTime

def test_basic():
  assert parse_time('') == Amount(0, Time.SECONDS)
  assert parse_time('1s') == Amount(1, Time.SECONDS)
  assert parse_time('2m60s') == Amount(3, Time.MINUTES)
  assert parse_time('1d') == Amount(1, Time.DAYS)
  assert parse_time('1d1H3600s') == Amount(26, Time.HOURS)
  assert parse_time('1d-1s') == Amount(86399, Time.SECONDS)

def test_bad():
  bad_strings = ['foo', 'dhms', '1s30d', 'a b c d', '  ', '1s2s3s']
  for bad_string in bad_strings:
    with pytest.raises(InvalidTime):
      parse_time(bad_string)

  bad_strings = [123, type]
  for bad_string in bad_strings:
    with pytest.raises(TypeError):
      parse_time(bad_string)
