from abc import ABCMeta, abstractmethod
import threading

class ClockInterface(object):
  __metaclass__ = ABCMeta

  @abstractmethod
  def time(self):
    pass

  @abstractmethod
  def tick(self, amount):
    pass

  @abstractmethod
  def sleep(self, amount):
    pass


class ThreadedClock(ClockInterface):
  FLOAT_FUDGE = 0.1

  def __init__(self, hz=100):
    self._rate = 1.0/hz
    self._time = 0
    self._cond = threading.Condition()
    self._waiters = []
    self._awaken = False
    self.reset()

  def reset(self):
    self._time = 0

  def time(self):
    return self._time

  def tick(self, amount=None):
    amount = amount or self._rate
    stop_at = self._time + amount
    while True:
      self._time += self._rate
      for stamp, cond in self._waiters[:]:
        if self._time >= stamp - (self._rate * self.FLOAT_FUDGE):
          with cond:
            cond.notify()
          while True:
            with self._cond:
              if self._awaken:
                break
              self._cond.wait()
          self._awaken = False
      if self._time >= stop_at:
        break

  def sleep(self, amount):
    wait_until = self._time + amount
    assert amount >= 0
    if amount == 0:
      return
    my_condition = threading.Condition()
    self._waiters.append((wait_until, my_condition))
    with my_condition:
      my_condition.wait()
    self._waiters.remove((wait_until, my_condition))
    with self._cond:
      self._awaken = True
      self._cond.notify()
