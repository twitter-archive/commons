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
import threading
import time
import zookeeper

from twitter.common.zookeeper.client import ZooKeeper
from twitter.common.zookeeper.test_server import ZookeeperServer

MAX_EVENT_WAIT_SECS = 30.0
MAX_EXPIRE_WAIT_SECS = 60.0
CONNECT_TIMEOUT_SECS = 10.0
CONNECT_RETRIES = 6

def make_zk(server, **kw):
  return ZooKeeper('localhost:%d' % server.zookeeper_port,
                   timeout_secs=CONNECT_TIMEOUT_SECS,
                   max_reconnects=CONNECT_RETRIES,
                   **kw)


def test_client_connect():
  with ZookeeperServer() as server:
    zk = make_zk(server)
    assert zk.get_children('/') == ['zookeeper']


def test_client_connect_times_out():
  import socket
  sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  sock.bind(('localhost', 0))
  _, port = sock.getsockname()
  sock.close()
  with pytest.raises(ZooKeeper.ConnectionTimeout):
    ZooKeeper('localhost:%d' % port, timeout_secs=1.0)


def test_client_reconnect():
  with ZookeeperServer() as server:
    zk = make_zk(server)
    zk.get_children('/')
    assert server.shutdown()

    # zk.get_children should block until reconnected
    children = []
    class GetThread(threading.Thread):
      def run(self):
        children.extend(zk.get_children('/'))
    gt = GetThread()
    gt.start()

    assert server.start()
    gt.join()

    assert children == ['zookeeper']


def test_expand_ensemble():
  assert ZooKeeper.expand_ensemble('localhost:1234') == '127.0.0.1:1234'


def test_async_while_headless():
  server = ZookeeperServer()

  disconnected = threading.Event()
  def on_event(zk, event, state, _):
    if zk._live.is_set() and state != zookeeper.CONNECTED_STATE:
      disconnected.set()

  zk = make_zk(server, watch=on_event)

  children = []
  completion_event = threading.Event()
  def children_completion(_, rc, results):
    children.extend(results)
    completion_event.set()

  assert server.shutdown()
  disconnected.wait(timeout=MAX_EVENT_WAIT_SECS)
  assert disconnected.is_set()

  zk.aget_children('/', None, children_completion)

  assert server.start()
  completion_event.wait(timeout=MAX_EVENT_WAIT_SECS)
  assert completion_event.is_set()

  assert children == ['zookeeper']

  server.stop()


def test_stopped():
  with ZookeeperServer() as server:
    zk = ZooKeeper('localhost:%d' % server.zookeeper_port)
    assert zk.get_children('/') == ['zookeeper']
    zk.stop()
    with pytest.raises(ZooKeeper.Stopped):
      zk.get_children('/')


def test_client_stops_propagate_through_completions():
  with ZookeeperServer() as server:
    zk = ZooKeeper('localhost:%d' % server.zookeeper_port)
    server.shutdown()

    # zk.get_children should block until reconnected
    stopped_event = threading.Event()
    class GetThread(threading.Thread):
      def run(self):
        try:
          zk.get_children('/')
        except ZooKeeper.Stopped:
          stopped_event.set()

    gt = GetThread()
    gt.start()
    time.sleep(0.1)  # guarantee an interpreter thread yield

    zk.stop()
    stopped_event.wait(timeout=MAX_EVENT_WAIT_SECS)
    assert stopped_event.is_set()


def test_session_event():
  with ZookeeperServer() as server:
    disconnected = threading.Event()
    def on_event(zk, event, state, _):
      if zk._live.is_set() and state != zookeeper.CONNECTED_STATE:
        disconnected.set()

    zk = ZooKeeper(server.ensemble, watch=on_event)
    session_id = zk.session_id()

    children = []
    completion_event = threading.Event()
    def children_completion(_, rc, results):
      children.extend(results)
      completion_event.set()

    server.shutdown()
    disconnected.wait(timeout=MAX_EVENT_WAIT_SECS)
    assert disconnected.is_set()

    zk.aget_children('/', None, children_completion)

    # expire session
    server.expire(session_id)
    server.start()

    completion_event.wait(timeout=MAX_EVENT_WAIT_SECS)
    assert completion_event.is_set()
    assert children == ['zookeeper']


def test_safe_operations():
  with ZookeeperServer() as server:
    zk = ZooKeeper(server.ensemble)
    assert zk.safe_create('/a/b/c/d') == '/a/b/c/d'
    session_id = zk.session_id()

    finish_event = threading.Event()
    class CreateThread(threading.Thread):
      def run(self):
        zk.safe_create('/foo/bar/baz/bak')
        finish_event.set()

    server.shutdown()
    server.expire(session_id)

    ct = CreateThread()
    ct.start()
    server.start()
    finish_event.wait(timeout=MAX_EXPIRE_WAIT_SECS)
    assert finish_event.is_set()
    assert zk.exists('/a/b/c/d')
    assert zk.exists('/foo/bar/baz/bak')

    session_id = zk.session_id()

    assert zk.safe_delete('/a')

    delete_event = threading.Event()
    class DeleteThread(threading.Thread):
      def run(self):
        zk.safe_delete('/foo')
        delete_event.set()

    server.shutdown()
    server.expire(session_id)

    dt = DeleteThread()
    dt.start()
    server.start()

    delete_event.wait(timeout=MAX_EXPIRE_WAIT_SECS)
    assert delete_event.is_set()
    assert not zk.exists('/a')
    assert not zk.exists('/foo')
