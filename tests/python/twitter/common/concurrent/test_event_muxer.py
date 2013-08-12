from threading import Event

from twitter.common.concurrent import EventMuxer

import pytest


def test_basic_muxer():
  # timeout, no events set
  muxer = EventMuxer(Event(), Event())
  assert not muxer.wait(timeout=0.1)

  # no re-entry
  with pytest.raises(RuntimeError):
    muxer.wait()

  # bad init
  with pytest.raises(ValueError):
    EventMuxer(Event(), 'not_an_event')


@pytest.mark.skipif("sys.version_info >= (2, 7)")
def test_wait_without_return_values():
  e1, e2 = Event(), Event()
  e1.set()
  assert not EventMuxer(e1, e2).wait()


@pytest.mark.skipif("sys.version_info < (2, 7)")
def test_wait_with_return_values():
  e1, e2 = Event(), Event()
  e1.set()
  assert EventMuxer(e1, e2).wait()
