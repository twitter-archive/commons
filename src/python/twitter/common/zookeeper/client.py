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
import socket
import threading
import zookeeper
from functools import wraps

try:
  from twitter.common import app
  WITH_APP=True
except ImportError:
  WITH_APP=False

try:
  from twitter.common import log
  from twitter.common.log.options import LogOptions
except ImportError:
  import logging as log

try:
  from Queue import Queue, Empty
except ImportError:
  from queue import Queue, Empty

from .named_value import NamedValue

if WITH_APP:
  app.add_option(
      '--zookeeper',
      default='zookeeper.local.twitter.com:2181',
      metavar='HOST:PORT[,HOST:PORT,...]',
      help='Comma-separated list of host:port of ZooKeeper servers')
  app.add_option(
      '--zookeeper_timeout',
      type='float',
      default=5.0,
      help='default timeout (in seconds) for ZK operations')
  app.add_option(
      '--enable_zookeeper_debug_logging',
      dest='twitter_common_zookeeper_debug',
      default=False,
      action='store_true',
      help='whether to enable ZK debug logging to stderr')

  class ZookeeperLoggingSubsystem(app.Module):
    # Map the ZK debug log to the same level as stderr logging.
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
      log_level = LogOptions.stderr_log_level()
      zk_log_level = ZookeeperLoggingSubsystem._ZK_LOG_LEVEL_MAP.get(
          log_level, zookeeper.LOG_LEVEL_ERROR)
      zookeeper.set_debug_level(zk_log_level)

  app.register_module(ZookeeperLoggingSubsystem())




class ZooKeeper(object):
  """A convenience wrapper around the low-level ZooKeeper API.

  Blocks until the initial connection is established, and proxies method calls
  to the corresponding ZK functions, passing the handle.

  Supports both synchronous and asynchronous APIs.

  Syncronous API Notes:

    Synchronous calls will block across connection loss or session
    expiration until reconnected.

  Asynchronous API Notes:

    Asynchronous calls will queue up while the session/connection is
    unhealthy and only be dispatched while zookeeper is healthy.  It is
    still possible for asynchronous calls to fail should the session be
    severed after the call has been successfully dispatched.  In other
    words: don't assume your rc will always be zookeeper.OK.

    Watches will behave as normal assuming successful dispatch.  In general
    when using this wrapper, you should retry your call if your watch is
    fired with EXPIRED_SESSION_STATE and ignore anything else whose state is
    not CONNECTED_STATE.  This wrapper will never re-dispatch calls that
    have been sent to zookeeper without error.
  """

  class Error(Exception): pass
  class ConnectionTimeout(Error): pass
  class Stopped(Error): pass

  class Event(NamedValue):
    MAP = {
      0: 'UNKNOWN',
      zookeeper.CREATED_EVENT: 'CREATED',
      zookeeper.DELETED_EVENT: 'DELETED',
      zookeeper.CHANGED_EVENT: 'CHANGED',
      zookeeper.CHILD_EVENT: 'CHILD',
      zookeeper.SESSION_EVENT: 'SESSION',
      zookeeper.NOTWATCHING_EVENT: 'NOTWATCHING'
    }

    @property
    def map(self):
      return self.MAP

  class State(NamedValue):
    MAP = {
      0: 'UNKNOWN',
      zookeeper.CONNECTING_STATE: 'CONNECTING',
      zookeeper.ASSOCIATING_STATE: 'ASSOCIATING',
      zookeeper.CONNECTED_STATE: 'CONNECTED',
      zookeeper.EXPIRED_SESSION_STATE: 'EXPIRED_SESSION',
      zookeeper.AUTH_FAILED_STATE: 'AUTH_FAILED',
    }

    @property
    def map(self):
      return self.MAP

  class ReturnCode(NamedValue):
    MAP = {
      # Normal
      zookeeper.OK: 'OK',

      # Abnormal
      zookeeper.NONODE: 'NONODE',
      zookeeper.NOAUTH: 'NOAUTH',
      zookeeper.BADVERSION: 'BADVERSION',
      zookeeper.NOCHILDRENFOREPHEMERALS: 'NOCHILDRENFOREPHEMERALS',
      zookeeper.NODEEXISTS: 'NODEEXISTS',
      zookeeper.NOTEMPTY: 'NOTEMPTY',
      zookeeper.SESSIONEXPIRED: 'SESSIONEXPIRED',
      zookeeper.INVALIDCALLBACK: 'INVALIDCALLBACK',
      zookeeper.INVALIDACL: 'INVALIDACL',
      zookeeper.AUTHFAILED: 'AUTHFAILED',
      zookeeper.CLOSING: 'CLOSING',
      zookeeper.NOTHING: 'NOTHING',
      zookeeper.SESSIONMOVED: 'SESSIONMOVED',

      # Exceptional
      zookeeper.SYSTEMERROR: 'SYSTEMERROR',
      zookeeper.RUNTIMEINCONSISTENCY: 'RUNTIMEINCONSISTENCY',
      zookeeper.DATAINCONSISTENCY: 'DATAINCONSISTENCY',
      zookeeper.CONNECTIONLOSS: 'CONNECTIONLOSS',
      zookeeper.MARSHALLINGERROR: 'MARSHALLINGERROR',
      zookeeper.UNIMPLEMENTED: 'UNIMPLEMENTED',
      zookeeper.OPERATIONTIMEOUT: 'OPERATIONTIMEOUT',
      zookeeper.BADARGUMENTS: 'BADARGUMENTS',
      zookeeper.INVALIDSTATE: 'INVALIDSTATE'
    }

    @property
    def map(self):
      return self.MAP


  # White-list of methods that accept a ZK handle as their first argument
  _ZK_SYNC_METHODS = frozenset([
      'add_auth', 'close', 'create', 'delete', 'exists', 'get', 'get_acl',
      'get_children', 'is_unrecoverable', 'recv_timeout', 'set', 'set2',
      'set_acl', 'set_watcher', 'state',
   ])

  _ZK_ASYNC_METHODS = frozenset([
      'acreate', 'adelete', 'aexists', 'aget', 'aget_acl', 'aget_children', 'aset',
      'aset_acl', 'async'
  ])

  COMPLETION_RETRY = frozenset([
    zookeeper.CONNECTIONLOSS,
    zookeeper.OPERATIONTIMEOUT,
    zookeeper.SESSIONEXPIRED,
    zookeeper.CLOSING,
  ])

  @staticmethod
  def expand_ensemble(servers):
    """Expand comma-separated list of host:port to comma-separated, fully-resolved list of ip:port."""
    server_ports = []
    for server_port in servers.split(','):
      server, port = server_port.split(':')
      for ip in socket.gethostbyname_ex(server)[2]:
        server_ports.append('%s:%s' % (ip, port))
    return ','.join(server_ports)

  DEFAULT_TIMEOUT_SECONDS = 30.0
  DEFAULT_ENSEMBLE = 'localhost:2181'
  DEFAULT_ACL = [{ "perms": zookeeper.PERM_ALL, "scheme": "world", "id": "anyone" }]
  MAX_RECONNECTS = 1

  # (is live?, is stopped?) => human readable status
  STATUS_MATRIX = {
    (True, True): 'WTF',
    (True, False): 'OK',
    (False, True): 'STOPPED',
    (False, False): 'CONNECTING'
  }

  class Completion(object):
    def __init__(self, zk, function, *args, **kw):
      self._zk = zk
      self._logger = kw.pop('logger', log.debug)
      @wraps(function)
      def wrapper(zh):
        return function(zh, *args, **kw)
      self._fn = wrapper

    def __call__(self):
      try:
        self._logger('Completion(zh:%s, %s) start' % (self._zk._zh, self._fn.__name__))
        result = self._fn(self._zk._zh)
        self._logger('Completion(zh:%s, %s) success' % (self._zk._zh, self._fn.__name__))
        return result
      except TypeError as e:
        # Raced; zh now dead, so re-enqueue.
        if self._zk._zh is not None:
          raise
        self._zk._add_completion(self._fn)
      except (zookeeper.ConnectionLossException, SystemError):
        self._logger('Completion(zh:%s, %s) excepted, re-enqueueing' % (self._zk._zh, self._fn.__name__))
        self._zk._add_completion(self._fn)
      return zookeeper.OK

  # N.B.(wickman) This is code is theoretically racy.  We cannot synchronize
  # events across the zookeeper C event loop, however we do everything in
  # our power to catch transitional latches.  These are almost always
  # exercised in tests and never in practice.
  #
  # TODO(wickman) ConnectionLoss probably does not encapsulate all the
  # exception states that arise on connection loss and/or session
  # expiration.  However, we don't want to blanket catch ZooKeeperException
  # because some things e.g.  get() will raise NoNodeException.  We should
  # partition the exception space in two: behavioral exceptions and, well,
  # exceptional exceptions.
  class BlockingCompletion(Completion):
    def __call__(self):
      while True:
        try:
          self._logger('Completion(zh:%s, %s) start' % (self._zk._zh, self._fn.__name__))
          result = self._fn(self._zk._zh)
          self._logger('Completion(zh:%s, %s) success' % (self._zk._zh, self._fn.__name__))
          return result
        except (zookeeper.ConnectionLossException, TypeError) as e:
          # TypeError because we raced on live latch from True=>False when _zh gets reinitialized.
          if isinstance(e, TypeError) and self._zk._zh is not None:
            self._logger('Completion(zh:%s, %s) excepted, user error' % (self._zk._zh, self._fn.__name__))
            raise
          # We had the misfortune of the live latch being set but having a session event propagate
          # before the BlockingCompletion could be executed.
          while not self._zk._stopped.is_set():
            self._logger('Completion(zh:%s, live:%s, %s) excepted on connection event' % (
                self._zk._zh, self._zk._live.is_set(), self._fn.__name__))
            self._zk._live.wait(timeout=0.1)
            if self._zk._live.is_set():
              break
          if self._zk._stopped.is_set():
            raise ZooKeeper.Stopped('ZooKeeper is stopped.')

  def __init__(self,
               servers=None,
               timeout_secs=None,
               watch=None,
               max_reconnects=MAX_RECONNECTS,
               logger=log.debug):
    """Create new ZooKeeper object.

    Blocks until ZK negotation completes, or the timeout expires. By default
    only tries to connect once.  Use a larger 'max_reconnects' if you want
    to be resilient to things such as DNS outages/changes.

    If watch is set to a function, it is called whenever the global
    zookeeper watch is dispatched using the same function signature, with the
    exception that this object is used in place of the zookeeper handle.
    """

    default_ensemble = self.DEFAULT_ENSEMBLE
    default_timeout = self.DEFAULT_TIMEOUT_SECONDS
    if WITH_APP:
      options = app.get_options()
      default_ensemble = options.zookeeper
      default_timeout = options.zookeeper_timeout
    self._servers = servers or default_ensemble
    self._timeout_secs = timeout_secs or default_timeout
    self._init_count = 0
    self._live = threading.Event()
    self._stopped = threading.Event()
    self._completions = Queue()
    self._zh = None
    self._watch = watch
    self._logger = logger
    self._max_reconnects = max_reconnects
    self.reconnect()

  def __del__(self):
    self._safe_close()

  def _log(self, msg):
    self._logger('[zh:%s] %s' % (self._zh, msg))

  def session_id(self):
    session_id, _ = zookeeper.client_id(self._zh)
    return session_id

  def stop(self):
    """Gracefully stop this Zookeeper session."""
    self._log('Shutting down ZooKeeper')
    self._stopped.set()
    self._safe_close()
    self._completions = Queue()  # there is no .clear()

  def restart(self):
    """Stop and restart this Zookeeper session.  Unfinished completions will be retried
       on reconnection."""
    self._safe_close()
    self._stopped.clear()
    self.reconnect()

  def _safe_close(self):
    if self._zh is not None:
      zh, self._zh = self._zh, None
      try:
        zookeeper.close(zh)
      except zookeeper.ZooKeeperException:
        # the session has been corrupted or otherwise disconnected
        pass
      self._live.clear()

  def _add_completion(self, function, *args, **kw):
    self._completions.put(self.Completion(self, function, logger=self._log, *args, **kw))

  def _clear_completions(self):
    while self._live.is_set():
      try:
        completion = self._completions.get_nowait()
        completion()
        self._completions.task_done()
      except Empty:
        return

  def reconnect(self):
    """Attempt to reconnect to ZK."""
    if self._stopped.is_set():
      self._safe_close()
      return

    def connection_handler(handle, type, state, path):
      if self._zh != handle:
        try:
          # latent handle callback from previous connection
          zookeeper.close(handle)
        except:
          pass
        return
      if self._stopped.is_set():
        return
      if self._watch:
        self._watch(self, type, state, path)
      if state == zookeeper.CONNECTED_STATE:
        self._logger('Connection started, setting live.')
        self._live.set()
        self._clear_completions()
      elif state == zookeeper.EXPIRED_SESSION_STATE:
        self._logger('Session lost, clearing live state.')
        self._live.clear()
        self._zh = None
        self._init_count = 0
        self.reconnect()
      else:
        self._logger('Connection lost, clearing live state.')
        self._live.clear()

    # this closure is exposed for testing only -- in order to simulate session events.
    self._handler = connection_handler

    timeout_ms = int(self._timeout_secs * 1000)
    while True:
      self._safe_close()
      servers = self.expand_ensemble(self._servers)
      self._log('Connecting to ZK hosts at %s' % servers)
      self._zh = zookeeper.init(servers, connection_handler, timeout_ms)
      self._init_count += 1
      self._live.wait(self._timeout_secs + 1)
      if self._live.is_set():
        break
      elif self._init_count >= self._max_reconnects:
        self._safe_close()
        raise ZooKeeper.ConnectionTimeout('Timed out waiting for ZK connection to %s' % servers)
    self._log('Successfully connected to ZK at %s' % servers)

  def _wrap_sync(self, function_name):
    """Wrap a zookeeper module function in an error-handling completion that injects the
       current zookeeper handle as the first parameter."""
    function = getattr(zookeeper, function_name)
    @wraps(function)
    def _curry(*args, **kwargs):
      return self.BlockingCompletion(self, function, logger=self._log, *args, **kwargs)()
    return _curry

  def _wrap_async(self, function_name):
    """Wrap an asynchronous zookeeper module function in an error-handling
       completion that injects the current zookeeper handle as the first
       parameter and puts it on a completion queue if the current connection
       state is unhealthy."""
    function = getattr(zookeeper, function_name)
    @wraps(function)
    def _curry(*args, **kwargs):
      completion = self.Completion(self, function, logger=self._log, *args, **kwargs)
      if self._live.is_set():
        return completion()
      else:
        # TODO(wickman)  This is racy, should it go from not live => live
        # prior to Queue.put.  Two solutions: a periodic background thread
        # that attempts to empty the completion queue, or use a mutex-protected
        # container for self._live.
        self._completions.put(self.Completion(self, function, logger=self._log, *args, **kwargs))
        return zookeeper.OK  # proxy OK.
    return _curry

  def safe_create(self, path, acl=DEFAULT_ACL):
    child = '/'
    for component in filter(None, path.split('/')):
      child = posixpath.join(child, component)
      try:
        self.create(child, "", acl, 0)
      except zookeeper.NodeExistsException:
        continue
    return child

  def safe_delete(self, path):
    try:
      if not self.exists(path):
        return True
      for child in self.get_children(path):
        if not self.safe_delete(posixpath.join(path, child)):
          return False
      self.delete(path)
    except zookeeper.ZooKeeperException:
      return False
    return True

  def __getattr__(self, function_name):
    """Proxy to underlying ZK functions."""
    if function_name in ZooKeeper._ZK_SYNC_METHODS:
      return self._wrap_sync(function_name)
    elif function_name in ZooKeeper._ZK_ASYNC_METHODS:
      return self._wrap_async(function_name)
    else:
      raise AttributeError('%r has no attribute %r' % (self, function_name))

  def __str__(self):
    return 'ZooKeeper(status=%s,queued=%d,servers=%r)' % (
      self.STATUS_MATRIX[(self._live.is_set(), self._stopped.is_set())],
      self._completions.qsize(), self._servers)

  def __repr__(self):
    return 'ZooKeeper(servers=%r)' % self._servers
