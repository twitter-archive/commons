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

import os
import posixpath
import threading
import time
import unittest
import zookeeper
import thrift.TSerialization as codec
from twitter.common.zookeeper import ZooKeeper
from twitter.common.zookeeper.test_server import ZookeeperServer
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


if os.environ.get('ZOOKEEPER_TEST_DEBUGGING'):
  LAST_TIME = time.time()
  def sync_log(msg):
    global LAST_TIME
    now = time.time()
    with open('/tmp/zk_test.log', 'a+') as fp:
      fp.write('%10s - ' % ('%.1fms:' % (1000.0 * (now - LAST_TIME))))
      fp.write(msg + '\n')
    LAST_TIME = now
else:
  def sync_log(msg):
    pass


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
        sync_log('Creating %s' % path)
        self._zk.create(path, 'dir', [ZOO_OPEN_ACL_UNSAFE], 0)
        sync_log('  => OK')
      except zookeeper.NodeExistsException:
        sync_log('  => exists')
        pass

  def register(self, instance):
    sync_log('Registering instance: %s' % instance)
    return self._zk.create(
        posixpath.join(self._path, 'member_'),
        codec.serialize(instance),
        [ZOO_OPEN_ACL_UNSAFE],
        zookeeper.SEQUENCE,
        )

  def unregister(self, handle):
    sync_log('Canceling instance: %s' % handle)
    self._zk.delete(handle)


class TestServerSet(unittest.TestCase):
  def setUp(self):
    self._server = ZookeeperServer()

  def tearDown(self):
    self._server.stop()

  def test_client_iteration(self):
    sync_log('test_client_iteration')
    zk = ZooKeeper(self._server.ensemble, timeout_secs=10, logger=sync_log)
    service = ServerSet(zk, SERVICE_PATH)
    client = ServerSetClient(SERVICE_PATH, zk=zk)
    assert list(client) == []
    service.register(INSTANCE1)
    assert list(client) == [INSTANCE1]
    service.register(INSTANCE2)
    assert list(client) == [INSTANCE1, INSTANCE2]
    sync_log('Test over, killing client.')
    zk.close()

  def test_client_watcher(self):
    sync_log('test_client_watcher')

    updated = threading.Event()
    old_endpoints = ['init']
    new_endpoints = ['init']

    def watcher(service_path, old, new):
      old_endpoints[:] = old
      new_endpoints[:] = new
      updated.set()

    zk = ZooKeeper(self._server.ensemble, timeout_secs=10, logger=sync_log)
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

    zk.close()

  def test_client_handles_connection_recovery_gracefully(self):
    sync_log('test_client_handles_connection_recovery_gracefully')

    zk = ZooKeeper(self._server.ensemble, logger=sync_log)

    sync_log('Creating serverset.')
    service = ServerSet(zk, SERVICE_PATH)
    sync_log('  done')
    sync_log('Registering instance...')
    service.register(INSTANCE1)
    sync_log('  done')

    client = ServerSetClient(SERVICE_PATH, zk=zk)
    assert list(client) == [INSTANCE1]

    # Restart the server after delay
    threading.Timer(3.0, lambda: self._server.start()).start()
    self._server.shutdown()
    time.sleep(1.0)

    # This will block until the server returns, or the retry attempts fail (in
    # which cause it will throw an exception and the test will fail)
    assert list(client) == [INSTANCE1]
    sync_log('success.')

    zk.close()
