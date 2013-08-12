import threading
from functools import wraps

class Lockable(object):
  def __init__(self):
    self.__lock = threading.RLock()

  @staticmethod
  def sync(method):
    @wraps(method)
    def wrapper(self, *args, **kw):
      with self.__lock:
        return method(self, *args, **kw)
    return wrapper

  @property
  def lock(self):
    return self.__lock
