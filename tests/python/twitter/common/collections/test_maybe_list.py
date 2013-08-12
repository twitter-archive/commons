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

from functools import partial
import pytest

from twitter.common.collections import (
    maybe_list,
    OrderedDict,
    OrderedSet)


def test_default_maybe_list():
  HELLO_WORLD = ['hello', 'world']
  assert maybe_list('hello') == ['hello']
  assert maybe_list(('hello', 'world')) == HELLO_WORLD
  assert maybe_list(['hello', 'world']) == HELLO_WORLD
  assert maybe_list(OrderedSet(['hello', 'world', 'hello'])) == HELLO_WORLD
  assert maybe_list(s for s in ('hello', 'world')) == HELLO_WORLD
  od = OrderedDict(hello=1)
  od.update(world=2)
  assert maybe_list(od) == HELLO_WORLD
  assert maybe_list([]) == []
  assert maybe_list(()) == []
  assert maybe_list(set()) == []

  with pytest.raises(ValueError):
    maybe_list(123)
  with pytest.raises(ValueError):
    maybe_list(['hello', 123])

  assert maybe_list(['hello', 123], expected_type=(str, int)) == ['hello', 123]
  assert maybe_list(['hello', 123], expected_type=(int, str)) == ['hello', 123]


def test_maybe_list_types():
  iml = partial(maybe_list, expected_type=int)
  iml(1) == [1]
  iml([1,2]) == [1, 2]
  iml((1,2)) == [1, 2]
  iml(OrderedSet([1, 2, 1])) == [1, 2]
  iml(k for k in range(3)) == [0, 1, 2]
  iml([]) == []
  assert iml([]) == []
  assert iml(()) == []
  assert iml(set()) == []

  with pytest.raises(ValueError):
    iml('hello')
  with pytest.raises(ValueError):
    iml([123, 'hello'])
  with pytest.raises(ValueError):
    iml(['hello', 123])
