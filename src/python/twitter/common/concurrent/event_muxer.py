# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

from Queue import Empty, Queue
import threading

class EventMuxer(object):
  """Mux multiple threading.Events and trigger if any of them are set.

  This class is primarily of interest in the situation where multiple Events could trigger an
  action, but the specific one is not of interest.

  wait() _does not_ support re-entry; new objects should be instantiated as needed.

  Usage:
    >>> from twitter.common.concurrent import EventMuxer
    >>> e1, e2 = threading.Event(), threading.Event()
    # will block until e1 or e2 is set or timeout expires:
    >>> EventMuxer(e1, e2).wait(timeout=5)
    # will block indefinitely until e1 or e2 is set:
    >>> EventMuxer(e1, e2).wait()

  """
  class WaitThread(threading.Thread):
    """Wait on an event, and update a parent when the event occurs """
    def __init__(self, event, parentq):
      self.event = event
      self.parentq = parentq
      super(EventMuxer.WaitThread, self).__init__()
      self.daemon = True
    def run(self):
      self.parentq.put(self.event.wait(timeout=self.timeout))

  def __init__(self, *events):
    if not all(isinstance(arg, threading._Event) for arg in events):
      raise ValueError("arguments must be threading.Events()!")
    self._lock = threading.Lock()
    self._queue = Queue()
    self._wait_events = [self.WaitThread(event, self._queue) for event in events]
    self._started = False
    self._finished = False

  def wait(self, timeout=None):
    """ Wait until any of the dependent events are set, or the timeout expires

    While it should be usable across threads, there are two caveats:
      - Only the initial wait() call will pass on the timeout to the dependent events. (Subsequent
        EventMuxer.wait() calls will still time out as expected, but the original threads may
        potentially be longer-lived.)
      - This function does not support re-entry after any previous wait() call has returned (due to
        timeout or dependent event being set). Instantiate new EventMuxers as needed.

    Note: in Python <2.7, threading.Event.wait(timeout) does not indicate on return whether or not
    the timeout was reached. In this scenario, EventMuxer.wait() will always return False.

    In Python >=2.7, EventMuxer.wait() should return as per Event.wait():
      - True indicates one or more of the dependent events were set
      - False indicates that the timeout occurred before any of the events were set
    """
    with self._lock:
      if self._finished:
        raise RuntimeError("wait() does not support re-entry!")
      if not self._started:
        for thread in self._wait_events:
          thread.timeout = timeout
          thread.start()
        self._started = True
    try:
      if self._queue.get(timeout=timeout):
        return True
      return False
    except Empty:
      return False
    finally:
      with self._lock:
        self._finished = True
