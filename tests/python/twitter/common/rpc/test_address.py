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
from twitter.common.rpc import Address

def test_from_string():
  addr = Address.from_string('localhost:1234')
  assert addr.host == 'localhost'
  assert addr.port == 1234
  addr = Address.from_string('localhost: 1234')
  assert addr.host == 'localhost'
  assert addr.port == 1234

  with pytest.raises(Address.InvalidFormat):
    Address.from_string('localhost:')

  with pytest.raises(Address.InvalidFormat):
    Address.from_string('localhost:-1')

  with pytest.raises(Address.InvalidFormat):
    Address.from_string('localhost:beeblebrox')

  with pytest.raises(Address.InvalidFormat):
    Address.from_string('localhost:beeblebrox', host='localhost')


def test_from_pair():
  addr = Address.from_pair('localhost', 1234)
  assert addr.host == 'localhost'
  assert addr.port == 1234

  with pytest.raises(Address.InvalidFormat):
    addr = Address.from_pair(('localhost', 1234))

  with pytest.raises(Address.InvalidFormat):
    addr = Address.from_pair('localhost')

  with pytest.raises(Address.InvalidFormat):
    addr = Address.from_pair('localhost', 1, 2, 3, 4)

def test_from_tuple():
  addr = Address.from_tuple(('localhost', 1234))
  assert addr.host == 'localhost'
  assert addr.port == 1234

  with pytest.raises(Address.InvalidFormat):
    addr = Address.from_tuple('localhost', 1234)

  with pytest.raises(Address.InvalidFormat):
    addr = Address.from_tuple('localhost')

  with pytest.raises(Address.InvalidFormat):
    addr = Address.from_tuple(('localhost', 1), 2, 3, 4)


def test_from_address():
  addr = Address.from_tuple(('localhost', 1234))
  addr = Address.from_address(addr)
  assert addr.host == 'localhost'
  assert addr.port == 1234


def test_parse():
  def assert_kosher(addr):
    assert addr.host == 'localhost'
    assert addr.port == 1234

  addr = Address.parse('localhost:1234')
  assert_kosher(addr)
  addr = Address.parse('localhost', 1234)
  assert_kosher(addr)
  addr = Address.parse(('localhost', 1234))
  assert_kosher(addr)
  addr = Address.from_address(addr)
  assert_kosher(addr)
