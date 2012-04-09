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

import gc
import pytest
import time
from collections import namedtuple
try:
  from Queue import Empty
except ImportError:
  from queue import Empty
from twitter.common.resourcepool import ResourcePool
from twitter.common.quantity import Amount, Time


MyResource = namedtuple('MyResource', 'id')


class TestResourcePool(object):
  def setup_method(self, method):
    self.pool = ResourcePool([MyResource(i) for i in range(10)])

  def test_consume_resources(self):
    consumed = [self.pool.acquire() for _ in range(5)]
    assert self.pool._resources.qsize() == 5
    consumed.extend(self.pool.acquire() for _ in range(5))
    assert self.pool.empty()

  def test_consume_too_many_resources(self):
    _ = [self.pool.acquire() for _ in range(10)]
    with pytest.raises(Empty):
      self.pool.acquire(0.1)

  def test_context_manager(self):
    with self.pool.acquire() as resource:
      assert self.pool._resources.qsize() == 9
      assert resource.id == 0
      with self.pool.acquire() as r2:
        assert self.pool._resources.qsize() == 8
        assert r2.id == 1
    assert self.pool._resources.qsize() == 10

  def test_cleanup(self):
    def acquire():
      resource = self.pool.acquire()
      assert self.pool._resources.qsize() == 9

    acquire()
    # Make extra sure that resource has been freed
    gc.collect()
    assert self.pool._resources.qsize() == 10

  def test_wait_with_amount(self):
    pool = ResourcePool([])
    now = time.time()
    with pytest.raises(Empty):
      # TODO(wickman) We should also be able to round-down for non-integral Amount types.
      with pool.acquire(timeout=Amount(1, Time.SECONDS) +
                                Amount(10, Time.MILLISECONDS)) as resource:
        pass
    elapsed = time.time() - now
    assert elapsed >= 1.0

