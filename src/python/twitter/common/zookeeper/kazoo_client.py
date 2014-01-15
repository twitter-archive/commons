import logging
import threading
import sys

from twitter.common.metrics import (
    AtomicGauge,
    LambdaGauge,
    Observable)

from kazoo.client import KazooClient
from kazoo.protocol.states import KazooState, KeeperState


class TwitterKazooClient(KazooClient, Observable):
  @classmethod
  def make(cls, *args, **kw):
    verbose = kw.pop('verbose', False)
    async = kw.pop('async', True)
    if verbose is False:
      kw['logger'] = logging.Logger('kazoo.devnull', level=sys.maxsize)
    zk = cls(*args, **kw)
    if async:
      zk.start_async()
      zk.connecting.wait()
    else:
      zk.start()

    return zk

  def __init__(self, *args, **kw):
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
