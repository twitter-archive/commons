try:
  from Queue import Empty, Queue
except ImportError:
  from queue import Empty, Queue

from twitter.common.concurrent import defer
from twitter.common.contextutil import Timer
from twitter.common.testing.clock import ThreadedClock

import pytest



def test_defer():
  DELAY = 3

  clock = ThreadedClock()
  results = Queue(maxsize=1)

  def func():
    results.put_nowait('success')

  defer(func, delay=DELAY, clock=clock)

  with Timer(clock=clock) as timer:
    with pytest.raises(Empty):
      results.get_nowait()
    clock.tick(DELAY + 1)
    assert results.get() == 'success'

  assert timer.elapsed == DELAY + 1
