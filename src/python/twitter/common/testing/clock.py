from abc import abstractmethod
import threading
import time

from twitter.common.lang import Interface

class ClockInterface(Interface):

  @abstractmethod
  def time(self):
    pass

  @abstractmethod
  def tick(self, amount):
    pass

  @abstractmethod
  def sleep(self, amount):
    pass


class Handshake(object):
  def __init__(self):
    self._syn_event = threading.Event()
    self._ack_event = threading.Event()

  def syn(self):
    self._syn_event.wait()
    self._ack_event.set()

  def ack(self):
    self._syn_event.set()
    self._ack_event.wait()


class ThreadedClock(ClockInterface):
  def __init__(self, initial_value=0):
    self._time = initial_value
    self._waiters = []  # queue of [stop time, Handshake]

  def time(self):
    return self._time

  def _pop_waiter(self, end):
    times_up = sorted((waiter for waiter in self._waiters if waiter[0] <= end),
                       key=lambda element: element[0])
    if times_up:
      waiter = times_up[0]
      self._waiters.remove(waiter)
      return waiter

  def tick(self, amount):
    # yield thread, in case any others are waiting to sleep() on this clock
    time.sleep(0.1)
    now = self._time
    end = now + amount

    while True:
      waiter = self._pop_waiter(end)
      if not waiter:
        break

      waiter_time, waiter_handshake = waiter
      print('Time now: %s' % self._time)
      self._time = waiter_time
      waiter_handshake.ack()

    print('Time now: %s' % self._time)
    self._time = end

  def sleep(self, amount):
    waiter_end = self._time + amount
    waiter_handshake = Handshake()

    self._waiters.append((waiter_end, waiter_handshake))
    waiter_handshake.syn()
