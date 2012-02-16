import threading

class Lockable(object):
  def __init__(self):
    self.__lock = threading.RLock()

  @staticmethod
  def sync(method):
    def wrapper(self, *args, **kw):
      with self.__lock:
        return method(self, *args, **kw)
    return wrapper
