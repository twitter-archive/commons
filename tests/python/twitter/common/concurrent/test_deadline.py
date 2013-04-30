import time
from functools import partial

import pytest
from twitter.common.concurrent import deadline, Timeout

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
