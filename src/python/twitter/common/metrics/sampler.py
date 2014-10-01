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

import json
import os
import time
import threading

try:
  from twitter.common import log
except ImportError:
  log = None

from twitter.common.exceptions import ExceptionalThread
from twitter.common.quantity import Amount, Time

from .metrics import MetricProvider


class SamplerBase(ExceptionalThread):
  def __init__(self, period, clock):
    self._stop = threading.Event()
    self._period = period
    self._clock = clock
    ExceptionalThread.__init__(self)
    self.daemon = True

  def stop(self):
    self._stop.set()

  def is_stopped(self):
    return self._stop.is_set()

  def iterate(self):
    raise NotImplementedError

  def run(self):
    while True:
      self._clock.sleep(self._period.as_(Time.SECONDS))
      if self.is_stopped():
        break
      self.iterate()


class MetricSampler(SamplerBase, MetricProvider):
  """
    A thread that periodically samples from a MetricProvider and caches the
    samples.
  """
  def __init__(self, provider, period=Amount(1, Time.SECONDS), clock=time):
    self._provider = provider
    self._last_sample = self._provider.sample()
    self._lock = threading.Lock()
    SamplerBase.__init__(self, period, clock)
    self.daemon = True

  def sample(self):
    with self._lock:
      return self._last_sample

  def iterate(self):
    new_sample = self._provider.sample()
    with self._lock:
      self._last_sample = new_sample


class DiskMetricWriter(SamplerBase):
  """
    Takes a MetricProvider and periodically samples its values to disk in JSON format.
  """

  def __init__(self, provider, filename, period=Amount(15, Time.SECONDS), clock=time):
    self._provider = provider
    self._filename = filename
    SamplerBase.__init__(self, period, clock)
    self.daemon = True

  def iterate(self):
    with open(self._filename, 'w') as fp:
      json.dump(self._provider.sample(), fp)


class DiskMetricReader(SamplerBase, MetricProvider):
  """
    Given an input JSON file, periodically reads the contents from disk and exports it
    using the MetricProvider interface.
  """

  def __init__(self, filename, period=Amount(15, Time.SECONDS), clock=time):
    self._filename = filename
    self._sample = {}
    self._lock = threading.Lock()
    SamplerBase.__init__(self, period, clock)
    self.daemon = True

  def sample(self):
    with self._lock:
      return self._sample

  @property
  def age(self):
    try:
      return time.time() - os.path.getmtime(self._filename)
    except (IOError, OSError):
      return 0

  def iterate(self):
    with self._lock:
      try:
        with open(self._filename, 'r') as fp:
          self._sample = json.load(fp)
      except (IOError, OSError, ValueError) as e:
        if log:
          log.warn('Failed to collect sample: %s' % e)
