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


import socket
import threading
import zookeeper
from functools import wraps
from twitter.common import app
from twitter.common import log
from twitter.common.log.options import LogOptions
from twitter.common.quantity import Amount, Time


app.add_option(
    '--zookeeper',
    default='zookeeper.local.twitter.com:2181',
    metavar='HOST:PORT[,HOST:PORT,...]',
    help='Comma-separated list of host:port of ZooKeeper servers',
    )
app.add_option(
    '--zookeeper_timeout',
    type='float',
    default=5.0,
    help='default timeout (in seconds) for ZK operations',
    )
app.add_option(
    '--enable_zookeeper_debug_logging',
    dest='twitter_common_zookeeper_debug',
    default=False,
    action='store_true',
    help='whether to enable ZK debug logging to stdout',
    )


class ZookeeperLoggingSubsystem(app.Module):
  # Map the ZK debug log to the same level as stdout logging.
  _ZK_LOG_LEVEL_MAP = {
      log.DEBUG: zookeeper.LOG_LEVEL_DEBUG,
      log.INFO: zookeeper.LOG_LEVEL_INFO,
      log.WARN: zookeeper.LOG_LEVEL_WARN,
      log.ERROR: zookeeper.LOG_LEVEL_ERROR,
      log.FATAL: zookeeper.LOG_LEVEL_ERROR,
  }

  def __init__(self):
    app.Module.__init__(self, __name__, description='Zookeeper logging subsystem.')

  def setup_function(self):
    if app.get_options().twitter_common_zookeeper_debug:
      zookeeper.set_debug_level(zookeeper.LOG_LEVEL_DEBUG)
    else:
      self._set_default_log_level()

  def _set_default_log_level(self):
    log_level = LogOptions.stdout_log_level()
    zk_log_level = ZookeeperLoggingSubsystem._ZK_LOG_LEVEL_MAP.get(
        log_level, zookeeper.LOG_LEVEL_ERROR)
    zookeeper.set_debug_level(zk_log_level)

app.register_module(ZookeeperLoggingSubsystem())


class ZooKeeper(object):
  """A convenience wrapper around the low-level ZooKeeper API.

  Blocks until the initial connection is established, and proxies method calls
  to the corresponding ZK functions, passing the handle.

  eg.
    zk = ZooKeeper('localhost:2181')
    print zk.get_children('/')

  See the "zookeeper" module for details on indiviual methods.

  :param servers: Comma-separated list of host:ports to connect to. Defaults to
      --zookeeper.
  :param timeout: Timeout, in seconds, for ZK operations (including
      establishment). Defaults to --zookeeper_timeout.

  :raise ConnectionTimeout: If ZK negotiation times out.
  """

  class ConnectionTimeout(Exception): pass

  # White-list of methods that accept a ZK handle as their first argument
  _ZK_PROXY_METHODS = frozenset([
      'acreate', 'add_auth', 'adelete', 'aexists', 'aget', 'aget_acl',
      'aget_children', 'aset', 'aset_acl', 'async', 'close', 'create', 'delete',
      'exists', 'get', 'get_acl', 'get_children', 'is_unrecoverable', 'recv_timeout',
      'set', 'set2', 'set_acl', 'set_watcher', 'state',
  ])

  def __init__(self, servers=None, timeout=None):
    """Create new ZooKeeper object.

    Blocks until ZK negotation completes, or the timeout expires.
    """
    options = app.get_options()
    self._servers = servers or options.zookeeper
    self._timeout = options.zookeeper_timeout if timeout is None else timeout
    self._zh = None
    self.reconnect()

  def __del__(self):
    if self._zh is not None:
      zookeeper.close(self._zh)

  def reconnect(self):
    """Attempt to reconnect to ZK."""
    if self._zh is not None:
      # Dance around in case .close() throws an exception and __del__ is
      # triggered on a handle in a bad state.
      zh = self._zh
      self._zh = None
      zookeeper.close(zh)
    ready = threading.Event()

    def on_ready(handle, type, state, path):
      if state == zookeeper.CONNECTED_STATE:
        ready.set()

    # Initialise ZK handle, blocking until the on_ready callback is triggered.
    timeout = self._timeout
    timeout_ms = Amount(timeout, Time.SECONDS).as_(Time.MILLISECONDS)
    servers = _expand_server_port_list(self._servers)
    log.info('Connecting to ZK hosts at %s' % servers)
    self._zh = zookeeper.init(servers, on_ready, int(timeout_ms))
    ready.wait(timeout + 1)
    if not ready.is_set():
      raise ZooKeeper.ConnectionTimeout('Timed out waiting for ZK connection to %s' % servers)
    log.info('Successfully connected to ZK at %s' % servers)

  def __getattr__(self, function_name):
    """Proxy to raw ZK functions, passing handle as the first argument.  """
    if function_name not in ZooKeeper._ZK_PROXY_METHODS:
      raise AttributeError('%r has no attribute %r' % (self, function_name))
    function = getattr(zookeeper, function_name)

    @wraps(function)
    def _curry(*args, **kwargs):
      return function(self._zh, *args, **kwargs)
    return _curry

  def __repr__(self):
    return 'ZooKeeper(servers=%r)' % self._servers


def _expand_server_port_list(servers):
  """Expand comma-separated list of host:port to comma-separated, fully-resolved list of ip:port."""
  server_ports = []
  for server_port in servers.split(','):
    server, port = server_port.split(':')
    for ip in socket.gethostbyname_ex(server)[2]:
      server_ports.append('%s:%s' % (ip, port))
  return ','.join(server_ports)
