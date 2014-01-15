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
import pytest
import socket
import threading
import time
import zookeeper

from twitter.common import log
from twitter.common.log.options import LogOptions

from twitter.common.zookeeper.client import ZooKeeper, ZooDefs
from twitter.common.zookeeper.test_server import ZookeeperServer

import mox

MAX_EVENT_WAIT_SECS = 30.0
MAX_EXPIRE_WAIT_SECS = 60.0
CONNECT_TIMEOUT_SECS = 10.0
CONNECT_RETRIES = 6


if os.getenv('ZOOKEEPER_TEST_DEBUG'):
  LogOptions.set_stderr_log_level('NONE')
  LogOptions.set_disk_log_level('DEBUG')
  LogOptions.set_log_dir('/tmp')
  log.init('client_test')


def make_zk(server, **kw):
  return ZooKeeper('localhost:%d' % server.zookeeper_port,
                   timeout_secs=CONNECT_TIMEOUT_SECS,
                   max_reconnects=CONNECT_RETRIES,
                   **kw)


def test_client_connect():
  with ZookeeperServer() as server:
    zk = make_zk(server)
    assert zk.get_children('/') == ['zookeeper']


def sha_password_digest(username, password):
  import base64, hashlib
  return base64.b64encode(hashlib.sha1(username + ':' + password).digest())


def test_client_connect_with_auth():
  with ZookeeperServer() as server:
    zk = make_zk(server, authentication=('digest', 'username:password'))
    finish_event = threading.Event()

    def run_create_tests():
      zk.create('/unprotected_znode', 'unprotected content', ZooDefs.Acls.OPEN_ACL_UNSAFE)
      _, unprotected_acl = zk.get_acl('/unprotected_znode')
      zk.create('/protected_znode', 'protected content', ZooDefs.Acls.CREATOR_ALL_ACL)
      _, protected_acl = zk.get_acl('/protected_znode')
      assert unprotected_acl == ZooDefs.Acls.OPEN_ACL_UNSAFE
      assert len(protected_acl) == 1
      assert protected_acl[0]['perms'] == ZooDefs.Acls.CREATOR_ALL_ACL[0]['perms']
      assert protected_acl[0]['scheme'] == 'digest'
      assert protected_acl[0]['id'] == 'username:%s' % sha_password_digest('username', 'password')
      content, _ = zk.get('/unprotected_znode')
      assert content == 'unprotected content'
      content, _ = zk.get('/protected_znode')
      assert content == 'protected content'
      zk.delete('/unprotected_znode')
      zk.delete('/protected_znode')
      finish_event.set()

    # run normally
    run_create_tests()
    finish_event.wait()

    # run after connection loss
    assert server.shutdown()
    finish_event.clear()
    class BackgroundTester(threading.Thread):
      def run(self):
        run_create_tests()
    BackgroundTester().start()
    server.start()
    finish_event.wait()

    # run after session loss
    session_id = zk.session_id
    assert server.shutdown()
    finish_event.clear()
    BackgroundTester().start()
    server.expire(session_id)
    server.start()
    finish_event.wait()


def test_client_connect_times_out():
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
  m = mox.Mox()
  m.StubOutWithMock(socket, 'gethostbyname_ex')
  socket.gethostbyname_ex('localhost').AndReturn(('localhost', [], ['foo']))
  socket.gethostbyname_ex('localhost').AndReturn(('localhost', [], ['bar']))
  socket.gethostbyname_ex('localhost').AndReturn(('localhost', [], ['baz', 'bak']))
  m.ReplayAll()

  assert ZooKeeper.expand_ensemble('localhost:1234') == 'foo:1234'
  assert ZooKeeper.expand_ensemble('localhost:1234,localhost') == 'bar:1234,baz:2181,bak:2181'

  m.UnsetStubs()
  m.VerifyAll()


def test_bad_ensemble():
  with pytest.raises(ZooKeeper.InvalidEnsemble):
     ZooKeeper.expand_ensemble('localhost:')

  with pytest.raises(ZooKeeper.InvalidEnsemble):
     ZooKeeper.expand_ensemble('localhost:sheeps')

  m = mox.Mox()
  m.StubOutWithMock(socket, 'gethostbyname_ex')
  socket.gethostbyname_ex('zookeeper.twitter.com').AndRaise(
      socket.gaierror(8, 'nodename nor servname provided, or not known'))
  m.ReplayAll()

  with pytest.raises(ZooKeeper.InvalidEnsemble):
    ZooKeeper.expand_ensemble('zookeeper.twitter.com:2181')

  m.UnsetStubs()
  m.VerifyAll()


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
    session_id = zk.session_id

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
    session_id = zk.session_id

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

    session_id = zk.session_id

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


def test_safe_create():
  with ZookeeperServer() as server:
    zk_auth = ZooKeeper(server.ensemble, authentication=('digest', 'jack:jill'))

    zk_auth.safe_create('/a', acl=ZooDefs.Acls.EVERYONE_READ_CREATOR_ALL)
    assert zk_auth.exists('/a')

    zk_noauth = ZooKeeper(server.ensemble)
    with pytest.raises(zookeeper.NoAuthException):
      zk_noauth.safe_create('/a/b')
    assert not zk_auth.exists('/a/b')

    zk_auth.safe_create('/a/b', acl=ZooDefs.Acls.OPEN_ACL_UNSAFE)
    assert zk_noauth.exists('/a/b')

    zk_noauth.safe_create('/a/b/c')
    assert zk_noauth.exists('/a/b/c')


def test_metrics():
  with ZookeeperServer() as server:
    event = threading.Event()
    def watch_set(*args):
      event.set()
    zk = ZooKeeper(server.ensemble, watch=watch_set)
    zk._live.wait(timeout=MAX_EVENT_WAIT_SECS)
    sample = zk.metrics.sample()
    assert sample['live'] == 1
    assert sample['session_id'] == zk.session_id
    assert sample['session_expirations'] == 0
    assert sample['connection_losses'] == 0
    old_session_id = zk.session_id

    event.clear()
    server.expire(zk.session_id)
    event.wait(timeout=MAX_EXPIRE_WAIT_SECS)
    zk._live.wait(timeout=MAX_EVENT_WAIT_SECS)

    sample = zk.metrics.sample()
    assert sample['live'] == 1
    assert sample['session_id'] == zk.session_id
    assert old_session_id != zk.session_id
    assert sample['session_expirations'] == 1
