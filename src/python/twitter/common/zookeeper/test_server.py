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

from __future__ import print_function

import atexit
import errno
import os
import signal
import socket
import subprocess
import sys
import tempfile
import threading
import time

from twitter.common.contextutil import environment_as
from twitter.common.dirutil import safe_rmtree
from twitter.common.rpc import make_client
from twitter.common.rpc.finagle import TFinagleProtocol

from gen.twitter.common.zookeeper.testing.angrybird import ZooKeeperThriftServer
from gen.twitter.common.zookeeper.testing.angrybird.ttypes import (
  ExpireSessionRequest,
  ResponseCode)

from thrift.transport.TTransport import TTransportException

try:
  import zookeeper
  HAS_ZKPYTHON = True
except ImportError:
  HAS_ZKPYTHON = False


def get_random_port():
  s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  s.bind(('localhost', 0))
  _, port = s.getsockname()
  s.close()
  return port


class ZookeeperServer(object):
  """Manage local temporary instances of ZK for testing.

  Can also be used as a context manager, in which case it will start the first
  ZK server in the cluster and returns its port.
  """

  class Error(Exception): pass
  class InvalidServerId(Error): pass
  class NotStarted(Error): pass

  BUILD_COMMAND = """
    ./pants binary src/java/com/twitter/common/zookeeper/testing/angrybird
  """
  COMMAND = "java -jar dist/angrybird.jar -thrift_port %(thrift_port)s -zk_port %(zookeeper_port)s"
  BUILT = False
  CONNECT_RETRIES = 5
  CONNECT_BACKOFF_SECS = 6.0
  INITIAL_BACKOFF = 1.0
  _orphaned_pids = set()

  @classmethod
  def build(cls):
    if not cls.BUILT:
      pex_keys = {}
      for key in os.environ:
        if key.startswith('PEX'):
          pex_keys[key] = os.environ[key]
          os.unsetenv(key)
      assert subprocess.call(cls.BUILD_COMMAND.split()) == 0
      for key in pex_keys:
        os.putenv(key, pex_keys[key])
      cls.BUILT = True

  def __init__(self, zookeeper_port=None, thrift_port=None):
    self._service = None
    self._zh = None
    self.thrift_port = thrift_port or get_random_port()
    self.zookeeper_port = zookeeper_port or get_random_port()
    self.build()
    command = self.COMMAND % {
        'thrift_port': self.thrift_port, 'zookeeper_port': self.zookeeper_port}
    self._po = subprocess.Popen(command.split())
    self._orphaned_pids.add(self._po.pid)
    self.angrybird = self.setup_thrift()

  @property
  def zh(self):
    if self._po is None:
      raise self.NotStarted('Cluster has not been started!')
    if not HAS_ZKPYTHON:
      raise self.Error('No Zookeeper client library available!')
    if self._zh is None:
      start_event = threading.Event()
      def alive(zh, event, state, _):
        if event == zookeeper.SESSION_EVENT and state == zookeeper.CONNECTED_STATE:
          start_event.set()
      self._zh = zookeeper.init(self.ensemble, alive)
      start_event.wait()
    return self._zh

  def setup_thrift(self):
    if self._service is None:
      time.sleep(self.INITIAL_BACKOFF)
      for _ in range(self.CONNECT_RETRIES):
        try:
          self._service = make_client(ZooKeeperThriftServer, 'localhost', self.thrift_port,
            protocol=TFinagleProtocol)
          break
        except TTransportException:
          time.sleep(self.CONNECT_BACKOFF_SECS)
          continue
      else:
        raise self.NotStarted('Could not start Zookeeper cluster!')

      serverPortResponse = self._service.getZooKeeperServerPort()
      assert serverPortResponse.responseCode == ResponseCode.OK
      self.zookeeper_port = serverPortResponse.port
    return self._service

  @property
  def ensemble(self):
    if not self.zookeeper_port:
      raise self.NotStarted('Server not started!')
    return 'localhost:%d' % self.zookeeper_port

  def expire(self, session_id=None):
    if session_id is None:
      if self._zh is None:
        raise self.NotStarted('Must specify session id if no client connection available!')
      if not HAS_ZKPYTHON:
        raise self.Error('No Zookeeper client available!')
      session_id, _ = zookeeper.client_id(self._zh)
    expireResponse = self.angrybird.expireSession(ExpireSessionRequest(sessionId=session_id))
    return expireResponse.responseCode == ResponseCode.OK

  def shutdown(self):
    return self.angrybird.shutdown().responseCode == ResponseCode.OK

  def restart(self):
    return self.angrybird.restart().responseCode == ResponseCode.OK

  def start(self):
    return self.angrybird.startup().responseCode == ResponseCode.OK

  def stop(self):
    if self._po is None:
      raise self.NotStarted('Cluster not started!')
    self._po.kill()
    self._orphaned_pids.remove(self._po.pid)
    self._po = None
    self.thrift_port = None
    self.zookeeper_port = None

  def __enter__(self):
    return self

  def __exit__(self, exc_type, exc_value, traceback):
    self.stop()


@atexit.register
def _cleanup_orphans():
  for pid in ZookeeperServer._orphaned_pids:
    try:
      os.kill(pid, signal.SIGKILL)
    except OSError as e:
      if e.errno != errno.ESRCH:
        print('warning: error killing orphaned ZK server %d: %s' % (pid, e), file=sys.stderr)
