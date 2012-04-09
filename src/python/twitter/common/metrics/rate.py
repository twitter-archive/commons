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

from twitter.common.quantity import Amount, Time
from .gauge import NamedGauge, gaugelike, namablegauge

class Rate(NamedGauge):
  """
    Gauge that computes a windowed rate.
  """
  @staticmethod
  def of(gauge, name = None, window = None, clock = None):
    kw = {}
    if window: kw.update(window = window)
    if clock: kw.update(clock = clock)
    if name:
      if not gaugelike(gauge):
        raise TypeError('Rate.of must take a Gauge-like object!  Got %s' % type(gauge))
      return Rate(name, gauge, **kw)
    else:
      if not namablegauge(gauge):
        raise TypeError('Rate.of must take a namable Gauge-like object if no name specified!')
      return Rate(gauge.name(), gauge, **kw)

  def __init__(self, name, gauge, window = Amount(1, Time.SECONDS), clock = time):
    """
      Create a gauge using name as a base for a <name>_per_<window> sampling gauge.

        name: The base name of the gauge.
        gauge: The gauge to sample
        window: The window over which the samples should be measured (default 1 second.)
    """
    self._clock = clock
    self._gauge = gauge
    self._samples = []
    self._window = window
    NamedGauge.__init__(self, '%s_per_%s%s' % (name, window.amount(), window.unit()))

  def filter(self, newer_than=None):
    """
      Filter the samples to only contain elements in the window.
    """
    if newer_than is None:
      newer_than = self._clock.time() - self._window.as_(Time.SECONDS)
    self._samples = [sample for sample in self._samples if sample[0] >= newer_than]

  def read(self):
    now = self._clock.time()
    self.filter(now - self._window.as_(Time.SECONDS))
    new_sample = self._gauge.read()
    self._samples.insert(0, (now, new_sample))
    if len(self._samples) == 1:
      return 0
    last_sample = self._samples[-1]
    dy = new_sample - last_sample[1]
    dt = now - last_sample[0]
    return 0 if dt == 0 else dy / dt
