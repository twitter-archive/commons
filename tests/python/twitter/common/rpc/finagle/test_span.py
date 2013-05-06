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

from twitter.common.rpc.finagle.trace import SpanId


def test_span_from_value():
  # hex regex works
  with pytest.raises(SpanId.InvalidSpanId):
    SpanId.from_value('1234')
  assert SpanId.from_value('0000000000001234').value == int('1234', 16)
  assert SpanId.from_value(1234).value == 1234
  assert SpanId.from_value(SpanId(1234)).value == 1234
  assert SpanId.from_value(None).value is None
