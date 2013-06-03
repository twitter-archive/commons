import threading

from kazoo.client import KazooClient
from kazoo.protocol.states import KeeperState


# TODO(wickman) Patch kazoo client to have a self.connecting threading.Event
class TwitterKazooClient(KazooClient):
  @classmethod
  def make(cls, *args, **kw):
    zk = cls(*args, **kw)
    zk.start_async()
    zk.connecting.wait()
    return zk

  def __init__(self, *args, **kw):
    super(TwitterKazooClient, self).__init__(*args, **kw)
    self.connecting = threading.Event()

  def _session_callback(self, state):
    rc = super(TwitterKazooClient, self)._session_callback(state)
    if state == KeeperState.CONNECTING:
      self.connecting.set()
    return rc

  @property
  def live(self):
    return self._live
