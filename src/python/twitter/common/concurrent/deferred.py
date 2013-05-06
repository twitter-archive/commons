# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

import time

from twitter.common.exceptions import ExceptionalThread
from twitter.common.lang import Compatibility
from twitter.common.quantity import Amount, Time


class Deferred(ExceptionalThread):
  """
    Wrapper for a delayed closure.
  """
  def __init__(self, closure, delay=Amount(0, Time.SECONDS), clock=time):
    super(Deferred, self).__init__()
    self._closure = closure
    if isinstance(delay, Compatibility.numeric):
      self._delay = delay
    elif isinstance(delay, Amount) and isinstance(delay.unit(), Time):
      self._delay = delay.as_(Time.SECONDS)
    else:
      raise ValueError('Deferred must take a numeric or Amount of Time.')
    self._clock = clock
    self._initialized = clock.time()
    self.daemon = True

  def run(self):
    self._clock.sleep(self._delay)
    self._closure()


def defer(closure, **kw):
  """Run a closure with a specified delay on its own thread.

    Args:
      closure (function)
    Keyword args:
      delay (in seconds, or Amount of Time, default 0)
      clock (the clock to use for time() and sleep(), default time)
  """

  Deferred(closure, **kw).start()
