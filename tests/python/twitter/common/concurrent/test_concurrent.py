import time
from functools import partial
from Queue import Empty, Queue

import pytest
from twitter.common.concurrent import deadline, defer, Timeout
from twitter.common.contextutil import Timer


def test_deadline_default_timeout():
  timeout = partial(time.sleep, 0.5)
  with pytest.raises(Timeout):
    deadline(timeout)


def test_deadline_custom_timeout():
  timeout = partial(time.sleep, 0.2)
  with pytest.raises(Timeout):
    deadline(timeout, 0.1)


def test_deadline_no_timeout():
  assert 'success' == deadline(lambda: 'success')


def test_defer():
  DELAY = 0.5
  results = Queue(maxsize=1)
  def func():
    results.put_nowait('success')
  defer(func, delay=DELAY)
  with Timer() as timer:
    assert results.get() == 'success'
  assert timer.elapsed >= DELAY
