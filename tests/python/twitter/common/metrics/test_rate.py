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

import pytest
import unittest

from twitter.common.quantity import Amount, Time, Data
from twitter.common.metrics import (
  AtomicGauge,
  MutatorGauge,
  NamedGauge,
  Rate
)

class FakeGauge(NamedGauge):
  def __init__(self, name):
    self._supplies = []
    NamedGauge.__init__(self, name)

  def supplies(self, values):
    self._supplies.extend(values)
    return self

  def read(self):
    return self._supplies.pop(0)

class TestClock(object):
  def __init__(self):
    self._time = 0

  def advance(self, ticks):
    self._time += ticks

  def time(self):
    return self._time

class TestRate(unittest.TestCase):
  def test_empty(self):
    clock = TestClock()
    gauge = FakeGauge('test').supplies([100000])
    rate = Rate("foo", gauge, window = Amount(30, Time.SECONDS), clock=clock)
    assert rate.read() == 0

  def test_windowing(self):
    TEN_SECONDS = Amount(10, Time.SECONDS).as_(Time.SECONDS)

    clock = TestClock()
    gauge = FakeGauge('test').supplies([100, 0, 50, 100, 150, 100, 50])
    rate = Rate("foo", gauge, window = Amount(30, Time.SECONDS), clock=clock)

    assert rate.read() == 0

    clock.advance(TEN_SECONDS)
    assert -100.0 / 10 == rate.read()

    clock.advance(TEN_SECONDS)
    assert -50.0 / 20 == rate.read()

    clock.advance(TEN_SECONDS)
    assert 0 == rate.read()

    clock.advance(TEN_SECONDS)
    assert 150.0 / 30 == rate.read()

    clock.advance(TEN_SECONDS)
    assert 50.0 / 30 == rate.read()

    clock.advance(TEN_SECONDS)
    assert -50.0 / 30 == rate.read()

  def test_static_constructor(self):
    gauge = FakeGauge('test').supplies([100000])
    rate = Rate.of(gauge)
    assert rate.name() == 'test_per_1secs'
    rate = Rate.of(gauge, window = Amount(5, Time.MINUTES))
    assert rate.name() == 'test_per_5mins'
    rate = Rate.of(gauge, name = 'holyguacamole')
    assert rate.name() == 'holyguacamole_per_1secs'
    rate = Rate.of(gauge, name = 'holyguacamole', window = Amount(3, Time.HOURS))
    assert rate.name() == 'holyguacamole_per_3hrs'
