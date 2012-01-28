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

import posixpath
import pytest
import threading
import zookeeper
import thrift.TSerialization as codec
from twitter.common.zookeeper import ZooKeeper
from twitter.common.zookeeper.test_cluster import ZookeeperClusterBootstrapper
from twitter.common.zookeeper.serversets import ServerSetClient
from gen.twitter.thrift.endpoint.ttypes import ServiceInstance, Endpoint


ZOO_OPEN_ACL_UNSAFE = {
    'perms': zookeeper.PERM_ALL,
    'scheme': 'world',
    'id': 'anyone',
    }
SERVICE_PATH = '/twitter/service/test'
INSTANCE1 = ServiceInstance(
    status=2,
    additionalEndpoints={},
    serviceEndpoint=Endpoint(host='127.0.0.1', port=1234),
    )
INSTANCE2 = ServiceInstance(
    status=2,
    additionalEndpoints={},
    serviceEndpoint=Endpoint(host='127.0.0.1', port=1235),
    )


class ServerSet(object):
  """Nominal testing ServerSet registration implementation."""

  def __init__(self, zk, path):
    self._zk = zk
    self._path = path
    self._init()

  def _init(self):
    prefix = []
    for component in self._path.strip('/').split('/'):
      prefix.append(component)
      path = '/' + '/'.join(prefix)
      try:
        self._zk.create(path, 'dir', [ZOO_OPEN_ACL_UNSAFE], 0)
      except zookeeper.NodeExistsException:
        pass

  def register(self, instance):
    return self._zk.create(
        posixpath.join(self._path, 'member_'),
        codec.serialize(instance),
        [ZOO_OPEN_ACL_UNSAFE],
        zookeeper.SEQUENCE,
        )

  def unregister(self, handle):
    self._zk.delete(handle)


def test_client_iteration():
  with ZookeeperClusterBootstrapper() as port:
    zk = ZooKeeper('localhost:%d' % port, timeout=10)
    service = ServerSet(zk, SERVICE_PATH)
    client = ServerSetClient(SERVICE_PATH, zk=zk)
    assert list(client) == []
    service.register(INSTANCE1)
    assert list(client) == [INSTANCE1]
    service.register(INSTANCE2)
    assert list(client) == [INSTANCE1, INSTANCE2]


def test_client_watcher():
  with ZookeeperClusterBootstrapper() as port:
    updated = threading.Event()
    old_endpoints = ['init']
    new_endpoints = ['init']

    def watcher(service_path, old, new):
      old_endpoints[:] = old
      new_endpoints[:] = new
      updated.set()

    server = 'localhost:%d' % port
    zk = ZooKeeper(server, timeout=10)
    service = ServerSet(zk, SERVICE_PATH)
    client = ServerSetClient(SERVICE_PATH, zk=zk, watcher=watcher)

    updated.wait(2.0)
    assert updated.is_set()
    assert old_endpoints == []
    assert new_endpoints == []
    updated.clear()

    instance1 = service.register(INSTANCE1)
    updated.wait(2.0)
    assert updated.is_set()
    assert old_endpoints == []
    assert new_endpoints == [INSTANCE1]
    updated.clear()

    service.register(INSTANCE2)
    updated.wait(2.0)
    assert updated.is_set()
    assert old_endpoints == [INSTANCE1]
    assert new_endpoints == [INSTANCE1, INSTANCE2]
    updated.clear()

    service.unregister(instance1)
    updated.wait(2.0)
    assert updated.is_set()
    assert old_endpoints == [INSTANCE1, INSTANCE2]
    assert new_endpoints == [INSTANCE2]
    updated.clear()


def test_client_handles_connection_recovery_gracefully():
  cluster = ZookeeperClusterBootstrapper()
  with cluster as port:
    server = 'localhost:%d' % port
    zk = ZooKeeper(server)
    service = ServerSet(zk, SERVICE_PATH)
    service.register(INSTANCE1)

    client = ServerSetClient(SERVICE_PATH, zk=zk)
    assert list(client) == [INSTANCE1]

    # Restart the server after delay
    threading.Timer(3.0, lambda: cluster.start(1)).start()
    cluster.stop(1)
    # This will block until the server returns, or the retry attempts fail (in
    # which cause it will throw an exception and the test will fail)
    assert list(client) == [INSTANCE1]


def test_client_fails_after_retry():
  cluster = ZookeeperClusterBootstrapper()
  port = cluster.start(1)
  server = 'localhost:%d' % port
  zk = ZooKeeper(server, timeout=3)
  service = ServerSet(zk, SERVICE_PATH)
  service.register(INSTANCE1)

  # Specify a minimal number of retries so we trigger ReconnectFailed
  client = ServerSetClient(SERVICE_PATH, zk=zk, retries=1)
  assert list(client) == [INSTANCE1]

  cluster.stop(1)
  with pytest.raises(ServerSetClient.ReconnectFailed):
    list(client)
