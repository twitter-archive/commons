
import threading


class StoppableThread(threading.Thread):
  """A thread that can be stopped.

  The target function will be called in a tight loop until the thread is stopped.

  Note: Don't subclass this to override run(). That won't work. """
  def __init__(self, group=None, target=None, name=None, post_target=None, args=(), kwargs=None):
    if kwargs is None:
      kwargs = {}

    def stoppable_target():
      while True:
        target(*args, **kwargs)
        if post_target:
          post_target()
        with self._lock:
          if self._stopped:
            return

    threading.Thread.__init__(self, group=group, target=stoppable_target, name=name, args=args, kwargs=kwargs)
    self._lock = threading.Lock()  # Protects self._stopped.
    self._stopped = False

  def stop(self):
    """Blocks until the thread is joined."""
    with self._lock:
      self._stopped = True
    self.join()
