# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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
import time

from twitter.common.zookeeper.group.group_base import Membership
from twitter.common.zookeeper.serverset import (
    Endpoint,
    ServerSet,
    ServiceInstance)
from twitter.common.zookeeper.test_server import ZookeeperServer


class ServerSetTestBase(object):
  SERVICE_PATH = '/twitter/service/test'
  INSTANCE1 = Endpoint(host='127.0.0.1', port=1234)
  INSTANCE2 = Endpoint(host='127.0.0.1', port=1235)
  ADDITIONAL1 = {'http': Endpoint(host='127.0.0.1', port=8080)}
  ADDITIONAL2 = {'thrift': Endpoint(host='127.0.0.1', port=8081)}

  @classmethod
  def make_zk(cls, ensemble):
    raise NotImplementedError

  @classmethod
  def session_id(cls, client):
    raise NotImplementedError

  def setUp(self):
    self._server = ZookeeperServer()

  def tearDown(self):
    self._server.stop()

  def test_client_iteration(self):
    ss = ServerSet(self.make_zk(self._server.ensemble), self.SERVICE_PATH)
    assert list(ss) == []
    ss.join(self.INSTANCE1)
    assert list(ss) == [ServiceInstance(self.INSTANCE1)]
    ss.join(self.INSTANCE2)
    assert list(ss) == [ServiceInstance(self.INSTANCE1), ServiceInstance(self.INSTANCE2)]

  def test_async_client_iteration(self):
    ss1 = ServerSet(self.make_zk(self._server.ensemble), self.SERVICE_PATH)
    ss2 = ServerSet(self.make_zk(self._server.ensemble), self.SERVICE_PATH)
    ss1.join(self.INSTANCE1)
    ss2.join(self.INSTANCE2)
    assert list(ss1) == [ServiceInstance(self.INSTANCE1), ServiceInstance(self.INSTANCE2)]
    assert list(ss2) == [ServiceInstance(self.INSTANCE1), ServiceInstance(self.INSTANCE2)]

  def test_shard_id_registers(self):
    ss1 = ServerSet(self.make_zk(self._server.ensemble), self.SERVICE_PATH)
    ss2 = ServerSet(self.make_zk(self._server.ensemble), self.SERVICE_PATH)
    ss1.join(self.INSTANCE1, shard=0)
    ss2.join(self.INSTANCE2, shard=1)
    assert list(ss1) == [ServiceInstance(self.INSTANCE1, shard=0), ServiceInstance(self.INSTANCE2, shard=1)]
    assert list(ss2) == [ServiceInstance(self.INSTANCE1, shard=0), ServiceInstance(self.INSTANCE2, shard=1)]

  def test_canceled_join_long_time(self):
    zk = self.make_zk(self._server.ensemble)
    zk.live.wait()
    session_id = self.session_id(zk)
    ss = ServerSet(zk, self.SERVICE_PATH)
    join_signal = threading.Event()
    memberships = []

    def on_expire():
      pass

    def do_join():
      memberships.append(ss.join(self.INSTANCE1, expire_callback=on_expire))

    class JoinThread(threading.Thread):
      def run(_):
        while True:
          join_signal.wait()
          join_signal.clear()
          do_join()

    joiner = JoinThread()
    joiner.daemon = True
    joiner.start()

    do_join()
    assert len(memberships) == 1 and memberships[0] is not Membership.error()
    self._server.expire(session_id)
    self._server.shutdown()
    join_signal.set()
    self._server.start()
    while len(memberships) == 1:
      time.sleep(0.1)
    assert len(memberships) == 2 and memberships[1] is not Membership.error()

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

    service1 = ServerSet(self.make_zk(self._server.ensemble),
        self.SERVICE_PATH, on_join=on_join, on_leave=on_leave)
    service2 = ServerSet(self.make_zk(self._server.ensemble),
        self.SERVICE_PATH)

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
