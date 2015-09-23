# ==================================================================================================
# Copyright 2015 Twitter, Inc.
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

from __future__ import print_function

from abc import abstractmethod
import sys
import threading
import time
import traceback

from twitter.common.lang import Interface


class ClockInterface(Interface):

  @abstractmethod
  def time(self):
    """The current time.

    :rtype: float
    """
    pass

  @abstractmethod
  def tick(self, amount):
    """Advance the clock by `amount`.

    :param amount: The amount to advance the clock.
    :type amount: Anything that can be coerced to a float.
    """
    pass

  @abstractmethod
  def sleep(self, amount):
    """Block until the clock time is >= clock.time() + amount.

    :param amount: The amount of time to wait.
    :type amount: Anything that can be coerced to a float.
    """
    pass


class _Waiter(object):

  def __init__(self, wait_amount, wait_until):
    self.wait_amount = wait_amount  # the amount that this waiter is waiting
    self.wait_until = wait_until  # the time at which this waiter expires
    self.thread = threading.current_thread()  # the waiting thread
    self._syn_event = threading.Event()
    self._ack_event = threading.Event()

  def __lt__(self, other):
    if not isinstance(other, _Waiter):
      raise TypeError('Can only compare two Waiter objects.')
    return self.wait_until < other.wait_until

  def syn(self):
    self._syn_event.wait()
    self._ack_event.set()

  def ack(self):
    self._syn_event.set()
    self._ack_event.wait()


class ThreadedClock(ClockInterface):
  THREAD_YIELD_TIMEOUT = 0.1

  @classmethod
  def thread_yield(cls):
    time.sleep(cls.THREAD_YIELD_TIMEOUT)
    return cls.THREAD_YIELD_TIMEOUT

  def __init__(self, initial_value=0, log=None):
    """Construct a ThreadedClock.

    :keyword initial_value: The initial value of the clock. Defaults to 0.
    :keyword logger: A callable for accepting log messages.  Defaults to writing to sys.stderr.
    """
    self._time = float(initial_value)
    self._waiters = []  # queue of Waiters
    self._log = log or (lambda msg: sys.stderr.write(msg + '\n'))

  def converged(self, threads):
    """Determine whether supplied threads are either finished or sleeping on this clock.

    :param threads: An iterable of :class:`threading.Thread` objects to test for blocking.
    :returns: True if all threads are finished or sleeping on this clock, otherwise False.
    """
    thread_ids = set(thread.ident for thread in threads if thread.is_alive())
    waiting_ids = set(waiter.thread.ident for waiter in self._waiters
                      if waiter.thread.is_alive() and waiter.wait_until > self._time)
    return thread_ids == waiting_ids

  def converge(self, threads, timeout=None):
    """Wait until the supplied threads are finished or sleeping on this clock.

    This method should be called to ensure deterministic tests.

    :param threads: An iterable of :class:`threading.Thread` objects to test for blocking.
    :keyword timeout: A *real wall clock* timeout to wait for the threads to converge. If
        timeout is None, wait forever.
    :returns: True once all threads are finished or sleeping, False if the timeout expires
        without convergence.
    """
    # flush the queue at the current timeslice
    self.tick(0)
    total_time = 0
    while not self.converged(threads):
      total_time += self.thread_yield()
      if timeout and total_time >= timeout:
        return False
    return True

  def assert_waiting(self, thread, amount=None):
    """Make an assertion that `thread` is waiting, possibly for a specific `amount`.

    :param thread: A :class:`threading.Thread` object.
    :param amount: The amount that the thread should be waiting, if specified.
    """
    waiters = [waiter for waiter in self._waiters if waiter.thread == thread]
    if len(waiters) != 1:
      assert False, 'Thread %s is not currently sleeping.' % thread
    if amount is not None and waiters[0].wait_amount != amount:
      assert False, 'Thread %s is sleeping %s, expected %s.' % (
          thread, waiters[0].wait_amount, amount)

  def assert_not_waiting(self, thread):
    """Make an assertion that `thread` is not waiting.

    :param thread: A :class:`threading.Thread` object.
    """
    assert not any(waiter for waiter in self._waiters if waiter.thread == thread), (
       'Thread %s is unexpectedly waiting.' % thread)

  # --- Rest of the ClockInterface implementation.
  def time(self):
    return self._time

  def _pop_waiter(self, end):
    if self._waiters and self._waiters[0].wait_until <= end:
      return self._waiters.pop(0)

  def tick(self, amount):
    now = self._time
    end = now + amount

    while True:
      waiter = self._pop_waiter(end)
      if not waiter:
        break
      if self._log:
        self._log('[%r] Time now: %s' % (self, self._time))
      self._time = waiter.wait_until
      waiter.ack()

    if self._log:
      self._log('[%r] Time now: %s' % (self, self._time))

    self._time = end

  def sleep(self, amount):
    if amount < 0:
      # mirror time.time semantics.
      raise IOError('Cannot sleep < 0.')
    waiter = _Waiter(amount, self._time + amount)
    self._waiters.append(waiter)
    self._waiters.sort()
    waiter.syn()
