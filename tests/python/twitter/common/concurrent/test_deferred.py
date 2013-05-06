from Queue import Queue

import pytest
import time

from twitter.common.concurrent import defer
from twitter.common.contextutil import Timer
from twitter.common.testing.clock import ThreadedClock

def test_defer():
  clock = ThreadedClock()
  DELAY = 3
  results = Queue(maxsize=1)
  def func():
    results.put_nowait('success')
  defer(func, delay=DELAY, clock=clock)
  with Timer(clock=clock) as timer:
    clock.tick(4)
    assert results.get() == 'success'
  assert timer.elapsed >= DELAY
