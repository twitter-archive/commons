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

from collections import namedtuple
from functools import wraps
import posixpath
import random
import socket
import sys
import threading
import zookeeper

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

from twitter.common.metrics import (
    AtomicGauge,
    LambdaGauge,
    Observable)

try:
  from Queue import Queue, Empty
except ImportError:
  from queue import Queue, Empty

from .constants import Acl, Id


if WITH_APP:
  app.add_option(
      '--zookeeper',
      default='zookeeper.local.twitter.com:2181',
      metavar='HOST:PORT[,HOST:PORT,...]',
      dest='twitter_common_zookeeper_ensemble',
      help='A comma-separated list of host:port of ZooKeeper servers.')
  app.add_option(
      '--zookeeper_timeout',
      type='float',
      default=15.0,
      dest='twitter_common_zookeeper_timeout',
      help='The default timeout (in seconds) for ZK operations.')
  app.add_option(
      '--zookeeper_reconnects',
      type='int',
      default=0,
      dest='twitter_common_zookeeper_reconnects',
      help='The number of permitted reconnections before failing zookeeper (0 = infinite).')
  app.add_option(
      '--zookeeper_log_level',
      dest='twitter_common_zookeeper_log_level_override',
      choices=('NONE', 'DEBUG','INFO','WARN','ERROR','FATAL'),
      default='',
      help='Override the default ZK logging level.')

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
      log_level_override = app.get_options().twitter_common_zookeeper_log_level_override
      self._set_log_level(log_level_override=log_level_override)

    def _set_log_level(self, log_level_override=''):
      stderr_log_level = LogOptions.stderr_log_level()
      # set default level to FATAL.
      # we do this here (instead of add_option) to distinguish when an override is set.
      if stderr_log_level == log.INFO and log_level_override != 'INFO':
        stderr_log_level = log.FATAL
      # default to using stderr logging level, setting override if applicable
      log_level = getattr(log, log_level_override, stderr_log_level)
      # set the logger
      zk_log_level = ZookeeperLoggingSubsystem._ZK_LOG_LEVEL_MAP.get(
          log_level, zookeeper.LOG_LEVEL_ERROR)
      zookeeper.set_debug_level(zk_log_level)


  app.register_module(ZookeeperLoggingSubsystem())


class Perms(object):
  READ = zookeeper.PERM_READ
  WRITE = zookeeper.PERM_WRITE
  CREATE = zookeeper.PERM_CREATE
  DELETE = zookeeper.PERM_DELETE
  ADMIN = zookeeper.PERM_ADMIN
  ALL = zookeeper.PERM_ALL


class Ids(object):
  ANYONE_ID_UNSAFE = Id('world', 'anyone')
  AUTH_IDS = Id('auth', '')


class Acls(object):
  OPEN_ACL_UNSAFE = [Acl(Perms.ALL, Ids.ANYONE_ID_UNSAFE)]
  CREATOR_ALL_ACL = [Acl(Perms.ALL, Ids.AUTH_IDS)]
  READ_ACL_UNSAFE = [Acl(Perms.READ, Ids.ANYONE_ID_UNSAFE)]
  EVERYONE_READ_CREATOR_ALL = [Acl(Perms.ALL, Ids.AUTH_IDS), Acl(Perms.READ, Ids.ANYONE_ID_UNSAFE)]


class ZooDefs(object):
  Acls = Acls
  Ids = Ids
  Perms = Perms


del Acls, Ids, Perms


class ZooKeeper(Observable):
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
  class InvalidEnsemble(Error): pass
  class Stopped(Error): pass

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

  @classmethod
  def expand_ensemble(cls, servers):
    """Expand comma-separated list of host:port to comma-separated, fully-resolved list of ip:port."""
    server_ports = []
    for server_port in servers.split(','):
      server_split = server_port.split(':', 2)
      if len(server_split) == 1:
        server, port = server_split[0], cls.DEFAULT_PORT
      else:
        try:
          server, port = server_split[0], int(server_split[1])
        except ValueError:
          raise cls.InvalidEnsemble('Invalid ensemble string: %s' % server_port)
      try:
        for ip in socket.gethostbyname_ex(server)[2]:
          server_ports.append('%s:%s' % (ip, port))
      except socket.gaierror:
        raise cls.InvalidEnsemble('Could not resolve %s' % server)
    return ','.join(server_ports)

  DEFAULT_TIMEOUT_SECONDS = 30.0
  DEFAULT_ENSEMBLE = 'localhost:2181'
  DEFAULT_PORT = 2181
  DEFAULT_ACL = ZooDefs.Acls.OPEN_ACL_UNSAFE
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
      self._cid = random.randint(0, sys.maxint - 1)
      self._logger = kw.pop('logger', log.debug)
      @wraps(function)
      def wrapper(zh):
        return function(zh, *args, **kw)
      self._fn = wrapper
      self._logger('Created %s args:(%s) kw:{%s}' % (
        self,
        ', '.join(map(repr, args)),
        ', '.join('%s: %r' % (key, val) for key, val in kw.items())))

    def __str__(self):
      return '%s(id:%s, zh:%s, %s)' % (
          self.__class__.__name__, self._cid, self._zk._zh, self._fn.__name__)

    def __call__(self):
      try:
        self._logger('%s start' % self)
        result = self._fn(self._zk._zh)
        self._logger('%s success' % self)
        return result
      except TypeError as e:
        # Raced; zh now dead, so re-enqueue.
        if self._zk._zh is not None:
          raise
        self._logger('%s raced, re-enqueueing' % self)
        self._zk._add_completion(self._fn)
      except (zookeeper.ConnectionLossException,
              zookeeper.InvalidStateException,
              zookeeper.SessionExpiredException,
              SystemError) as e:
        self._logger('%s excepted (%s), re-enqueueing' % (self, e))
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
          self._logger('%s start' % self)
          result = self._fn(self._zk._zh)
          self._logger('%s success' % self)
          return result
        except (zookeeper.ConnectionLossException,
                zookeeper.InvalidStateException,
                zookeeper.SessionExpiredException,
                TypeError) as e:
          # TypeError because we raced on live latch from True=>False when _zh gets reinitialized.
          if isinstance(e, TypeError) and self._zk._zh is not None:
            self._logger('%s excepted, user error' % self)
            raise
          # We had the misfortune of the live latch being set but having a session event propagate
          # before the BlockingCompletion could be executed.
          while not self._zk._stopped.is_set():
            self._logger('%s [live: %s] excepted on connection event: %s' % (
                self, self._zk._live.is_set(), e))
            self._zk._live.wait(timeout=0.1)
            if self._zk._live.is_set():
              break
          if self._zk._stopped.is_set():
            raise ZooKeeper.Stopped('ZooKeeper is stopped.')
        except Exception as e:
          self._logger('%s excepted unexpectedly: %s' % (self, e))
          raise

  def __init__(self,
               servers=None,
               timeout_secs=None,
               watch=None,
               max_reconnects=None,
               authentication=None,
               logger=log.debug):
    """Create new ZooKeeper object.

    Blocks until ZK negotation completes, or the timeout expires. By default
    only tries to connect once.  Use a larger 'max_reconnects' if you want
    to be resilient to things such as DNS outages/changes.

    If watch is set to a function, it is called whenever the global
    zookeeper watch is dispatched using the same function signature, with the
    exception that this object is used in place of the zookeeper handle.

    If authentication is set, it should be a tuple of (scheme, credentials),
    for example, ('digest', 'username:password')
    """

    default_ensemble = self.DEFAULT_ENSEMBLE
    default_timeout = self.DEFAULT_TIMEOUT_SECONDS
    default_reconnects = self.MAX_RECONNECTS
    if WITH_APP:
      options = app.get_options()
      default_ensemble = options.twitter_common_zookeeper_ensemble
      default_timeout = options.twitter_common_zookeeper_timeout
      default_reconnects = options.twitter_common_zookeeper_reconnects
    self._servers = servers or default_ensemble
    self._timeout_secs = timeout_secs or default_timeout
    self._init_count = 0
    self._credentials = authentication
    self._authenticated = threading.Event()
    self._live = threading.Event()
    self._stopped = threading.Event()
    self._completions = Queue()
    self._zh = None
    self._watch = watch
    self._logger = logger
    self._max_reconnects = max_reconnects if max_reconnects is not None else default_reconnects
    self._init_metrics()
    self.reconnect()

  def __del__(self):
    self._safe_close()

  def _log(self, msg):
    self._logger('[zh:%s] %s' % (self._zh, msg))

  def _init_metrics(self):
    self._session_expirations = AtomicGauge('session_expirations')
    self._connection_losses = AtomicGauge('connection_losses')
    self.metrics.register(self._session_expirations)
    self.metrics.register(self._connection_losses)
    self.metrics.register(LambdaGauge('session_id', lambda: self.session_id))
    self.metrics.register(LambdaGauge('live', lambda: int(self._live.is_set())))

  @property
  def session_id(self):
    try:
      session_id, _ = zookeeper.client_id(self._zh)
      return session_id
    except:
      return None

  @property
  def session_expirations(self):
    return self._session_expirations.read()

  @property
  def connection_losses(self):
    return self._connection_losses.read()

  @property
  def live(self):
    return self._live

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

    def safe_close(zh):
      try:
        zookeeper.close(zh)
      except:
        # TODO(wickman) When the SystemError bug is fixed in zkpython, narrow this except clause.
        pass

    def activate():
      self._authenticated.set()
      self._live.set()

    def on_authentication(zh, rc):
      if self._zh != zh:
        safe_close(zh)
        return
      if rc == zookeeper.OK:
        activate()

    def maybe_authenticate():
      if self._authenticated.is_set() or not self._credentials:
        activate()
        return
      try:
        scheme, credentials = self._credentials
        zookeeper.add_auth(self._zh, scheme, credentials, on_authentication)
      except zookeeper.ZooKeeperException as e:
        self._logger('Failed to authenticate: %s' % e)

    def connection_handler(handle, type, state, path):
      if self._zh != handle:
        safe_close(handle)
        return
      if self._stopped.is_set():
        return
      if self._watch:
        self._watch(self, type, state, path)
      if state == zookeeper.CONNECTED_STATE:
        self._logger('Connection started, setting live.')
        maybe_authenticate()
        self._clear_completions()
      elif state == zookeeper.EXPIRED_SESSION_STATE:
        self._logger('Session lost, clearing live state.')
        self._session_expirations.increment()
        self._live.clear()
        self._authenticated.clear()
        self._zh = None
        self._init_count = 0
        self.reconnect()
      else:
        self._logger('Connection lost, clearing live state.')
        self._connection_losses.increment()
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
      elif self._max_reconnects > 0 and self._init_count >= self._max_reconnects:
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
      except zookeeper.NoAuthException:
        if not self.exists(child):
          raise
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
