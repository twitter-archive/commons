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

from Queue import Queue, Empty
from threading import Thread

from twitter.common.exceptions import ExceptionalThread
from twitter.common.lang import Compatibility
from twitter.common.quantity import Amount, Time


class Timeout(Exception):
  pass


def deadline(closure, timeout=Amount(150, Time.MILLISECONDS), daemon=False, propagate=False):
  """Run a closure with a timeout, raising an exception if the timeout is exceeded.

    args:
      closure   - function to be run (e.g. functools.partial, or lambda)
    kwargs:
      timeout   - in seconds, or Amount of Time, [default: Amount(150, Time.MILLISECONDS]
      daemon    - booleanish indicating whether to daemonize the thread used to run the closure
                  (otherwise, a timed-out closure can potentially exist beyond the life of the
                  calling thread) [default: False]
      propagate - booleanish indicating whether to re-raise exceptions thrown by the closure
                  [default: False]
  """
  if isinstance(timeout, Compatibility.numeric):
    pass
  elif isinstance(timeout, Amount) and isinstance(timeout.unit(), Time):
    timeout = timeout.as_(Time.SECONDS)
  else:
    raise ValueError('timeout must be either numeric or Amount of Time.')
  q = Queue(maxsize=1)
  class AnonymousThread(Thread):
    def __init__(self):
      super(AnonymousThread, self).__init__()
      self.daemon = bool(daemon)
    def run(self):
      try:
        result = closure()
      except Exception as result:
        if not propagate:
          # conform to standard behaviour of an exception being raised inside a Thread
          raise result
      q.put(result)
  AnonymousThread().start()
  try:
    result = q.get(timeout=timeout)
  except Empty:
    raise Timeout("Timeout exceeded!")
  else:
    if propagate and isinstance(result, Exception):
      raise result
    return result
