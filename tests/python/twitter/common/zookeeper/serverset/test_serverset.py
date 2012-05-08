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

import threading
import unittest
from twitter.common.zookeeper.client import ZooKeeper
from twitter.common.zookeeper.test_server import ZookeeperServer
from twitter.common.zookeeper.serverset import ServerSet, Endpoint, ServiceInstance


class TestServerSet(unittest.TestCase):
  SERVICE_PATH = '/twitter/service/test'
  INSTANCE1 = Endpoint(host='127.0.0.1', port=1234)
  INSTANCE2 = Endpoint(host='127.0.0.1', port=1235)
  ADDITIONAL1 = {'http': Endpoint(host='127.0.0.1', port=8080)}
  ADDITIONAL2 = {'thrift': Endpoint(host='127.0.0.1', port=8081)}

  def setUp(self):
    self._server = ZookeeperServer()

  def tearDown(self):
    self._server.stop()

  def test_client_iteration(self):
    ss = ServerSet(ZooKeeper(self._server.ensemble), self.SERVICE_PATH)
    assert list(ss) == []
    ss.join(self.INSTANCE1)
    assert list(ss) == [ServiceInstance(self.INSTANCE1)]
    ss.join(self.INSTANCE2)
    assert list(ss) == [ServiceInstance(self.INSTANCE1), ServiceInstance(self.INSTANCE2)]

  def test_async_client_iteration(self):
    ss1 = ServerSet(ZooKeeper(self._server.ensemble), self.SERVICE_PATH)
    ss2 = ServerSet(ZooKeeper(self._server.ensemble), self.SERVICE_PATH)
    ss1.join(self.INSTANCE1)
    ss2.join(self.INSTANCE2)
    assert list(ss1) == [ServiceInstance(self.INSTANCE1), ServiceInstance(self.INSTANCE2)]
    assert list(ss2) == [ServiceInstance(self.INSTANCE1), ServiceInstance(self.INSTANCE2)]

  def test_client_watcher(self):
    canceled_endpoints = []
    canceled = threading.Event()
    joined_endpoints = []
    joined = threading.Event()

    def on_join(endpoint):
      joined_endpoints[:] = [endpoint]
      joined.set()

    def on_leave(endpoint):
      canceled_endpoints[:] = [endpoint]
      canceled.set()

    service1 = ServerSet(ZooKeeper(self._server.ensemble), self.SERVICE_PATH,
                                   on_join=on_join, on_leave=on_leave)
    service2 = ServerSet(ZooKeeper(self._server.ensemble), self.SERVICE_PATH)

    member1 = service2.join(self.INSTANCE1)
    joined.wait(2.0)
    assert joined.is_set()
    assert not canceled.is_set()
    assert joined_endpoints == [ServiceInstance(self.INSTANCE1)]
    joined.clear()

    service2.join(self.INSTANCE2)
    joined.wait(2.0)
    assert joined.is_set()
    assert not canceled.is_set()
    assert joined_endpoints == [ServiceInstance(self.INSTANCE2)]
    joined.clear()

    service2.cancel(member1)
    canceled.wait(2.0)
    assert canceled.is_set()
    assert not joined.is_set()
    assert canceled_endpoints == [ServiceInstance(self.INSTANCE1)]
    canceled.clear()
