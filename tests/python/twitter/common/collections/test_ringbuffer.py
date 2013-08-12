# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

from twitter.common.collections import RingBuffer

def test_append():
  r = RingBuffer(5)
  for i in xrange(0, 5):
    r.append(i)
  assert (r[0], r[1], r[2], r[3], r[4]) == (0, 1, 2, 3, 4)
  for i in xrange(5, 10):
    r.append(i)
  assert (r[0], r[1], r[2], r[3], r[4]) == (5, 6, 7, 8, 9)

def test_circularity():
  r = RingBuffer(3)
  r.append(1)
  r.append(2)
  r.append(3)
  assert 1 in r
  assert (r[0], r[3], r[6], r[-3]) == (1, 1, 1, 1)
  r.append(4)
  assert 1 not in r
  assert (r[0], r[3], r[6], r[-3]) == (2, 2, 2, 2)

def test_bad_operations():
  with pytest.raises(ValueError):
    RingBuffer(0)
  r = RingBuffer()
  with pytest.raises(IndexError):
    r[1]
  with pytest.raises(IndexError):
    r[-1]
  r.append(1)
  with pytest.raises(RingBuffer.InvalidOperation):
    del r[0]
