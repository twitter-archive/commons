# ==================================================================================================
# Copyright 2011 Twitter, Inc.
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
import threading
from metrics import MetricProvider
from twitter.common.quantity import Amount, Time

try:
  from twitter.common import log
except ImportError:
  log = None

class MetricSampler(threading.Thread, MetricProvider):
  """
    A thread that periodically samples from a MetricProvider and caches the
    samples.
  """
  def __init__(self, metric_registry, period = Amount(1, Time.SECONDS)):
    self._registry = metric_registry
    self._period = period
    self._last_sample = self._registry.sample()
    self._lock = threading.Lock()
    self._shutdown = False
    threading.Thread.__init__(self)

  def sample(self):
    with self._lock:
      return self._last_sample

  def run(self):
    if log: log.debug('Starting metric sampler.')
    while not self._shutdown:
      time.sleep(self._period.as_(Time.SECONDS))
      new_sample = self._registry.sample()
      with self._lock:
        self._last_sample = new_sample

  def shutdown(self):
    if log: log.debug('Shutting down metric sampler.')
    self._shutdown = True
