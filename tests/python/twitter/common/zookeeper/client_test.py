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
import zookeeper
from twitter.common.zookeeper.client import ZooKeeper, _expand_server_port_list
from twitter.common.zookeeper.test_cluster import ZookeeperClusterBootstrapper
from twitter.common.zookeeper.port_allocator import EphemeralPortAllocator


def test_client_connect():
  with ZookeeperClusterBootstrapper() as port:
    zk = ZooKeeper('localhost:%d' % port)
    assert zk.get_children('/') == ['zookeeper']


def test_client_connect_times_out():
  ports = EphemeralPortAllocator()
  port = ports.allocate_port('zk')
  with pytest.raises(ZooKeeper.ConnectionTimeout):
    ZooKeeper('localhost:%d' % port, timeout=1.0)


def test_client_reconnect():
  cluster = ZookeeperClusterBootstrapper()
  port = cluster.start(1)
  zk = ZooKeeper('localhost:%d' % port)
  zk.get_children('/')
  cluster.stop(1)
  with pytest.raises(zookeeper.ConnectionLossException):
    zk.get_children('/')
  cluster.start(1)
  with pytest.raises(zookeeper.ConnectionLossException):
    zk.get_children('/')
  zk.reconnect()
  assert zk.get_children('/') == ['zookeeper']


def test_expand_server_port_list():
  assert _expand_server_port_list('localhost:1234') == '127.0.0.1:1234'
