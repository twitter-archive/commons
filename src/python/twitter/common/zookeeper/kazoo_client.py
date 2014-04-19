import logging
import threading
import sys

from twitter.common.metrics import (
    AtomicGauge,
    LambdaGauge,
    Observable)

from kazoo.client import KazooClient
from kazoo.protocol.states import KazooState, KeeperState
from kazoo.retry import KazooRetry


DEFAULT_RETRY_MAX_DELAY_SECS = 600


DEFAULT_RETRY_DICT = dict(
    max_tries=None,
    ignore_expire=True,
)


class TwitterKazooClient(KazooClient, Observable):
  @classmethod
  def make(cls, *args, **kw):
    # TODO(jcohen): Consider removing verbose option entirely in favor of just using loglevel.
    verbose = kw.pop('verbose', False)
    async = kw.pop('async', True)

    if verbose:
      loglevel = kw.pop('loglevel', logging.INFO)
    else:
      loglevel = kw.pop('loglevel', sys.maxsize)

    logger = logging.getLogger('kazoo.devnull')
    logger.setLevel(loglevel)
    kw['logger'] = logger

    zk = cls(*args, **kw)
    if async:
      zk.start_async()
      zk.connecting.wait()
    else:
      zk.start()

    return zk

  def __init__(self, *args, **kw):
    if 'connection_retry' not in kw:
      # The default backoff delay limit in kazoo is 3600 seconds, which is generally
      # too conservative for our use cases.  If not supplied by the caller, provide
      # a backoff that will truncate earlier.
      kw['connection_retry'] = KazooRetry(
          max_delay=DEFAULT_RETRY_MAX_DELAY_SECS, **DEFAULT_RETRY_DICT)

    super(TwitterKazooClient, self).__init__(*args, **kw)
    self.connecting = threading.Event()
    self.__session_expirations = AtomicGauge('session_expirations')
    self.__connection_losses = AtomicGauge('connection_losses')
    self.__session_id = LambdaGauge('session_id', lambda: (self._session_id or 0))
    self.metrics.register(self.__session_expirations)
    self.metrics.register(self.__connection_losses)
    self.metrics.register(self.__session_id)
    self.add_listener(self._observable_listener)

  def _observable_listener(self, state):
    if state == KazooState.LOST:
      self.__session_expirations.increment()
    elif state == KazooState.SUSPENDED:
      self.__connection_losses.increment()

  def _session_callback(self, state):
    rc = super(TwitterKazooClient, self)._session_callback(state)
    if state == KeeperState.CONNECTING:
      self.connecting.set()
    return rc

  @property
  def live(self):
    return self._live
