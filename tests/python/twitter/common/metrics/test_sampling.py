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

import os
import pytest

from twitter.common.contextutil import temporary_file
from twitter.common.metrics import Label
from twitter.common.metrics.metrics import Metrics
from twitter.common.metrics.sampler import (
    MetricSampler,
    SamplerBase,
    DiskMetricWriter,
    DiskMetricReader)
from twitter.common.quantity import Amount, Time
from twitter.common.testing.clock import ThreadedClock


def test_sampler_base():
  class TestSampler(SamplerBase):
    def __init__(self, period, clock):
      self.count = 0
      SamplerBase.__init__(self, period, clock)

    def iterate(self):
      self.count += 1

  test_clock = ThreadedClock()
  sampler = TestSampler(Amount(1, Time.SECONDS), clock=test_clock)
  sampler.start()

  assert test_clock.converge(threads=[sampler])
  test_clock.assert_waiting(sampler, 1)

  test_clock.tick(0.5)
  assert test_clock.converge(threads=[sampler])
  assert sampler.count == 0

  test_clock.tick(0.5)
  assert test_clock.converge(threads=[sampler])
  assert sampler.count == 1

  test_clock.tick(5)
  assert test_clock.converge(threads=[sampler])
  assert sampler.count == 6

  assert not sampler.is_stopped()
  sampler.stop()

  # make sure that stopping the sampler short circuits any sampling
  test_clock.tick(5)
  assert test_clock.converge(threads=[sampler])
  assert sampler.count == 6


def test_metric_read_write():
  metrics = Metrics()

  with temporary_file() as fp:
    os.unlink(fp.name)

    writer = DiskMetricWriter(metrics, fp.name)
    reader = DiskMetricReader(fp.name)

    assert reader.sample() == {}
    reader.iterate()
    assert reader.sample() == {}

    writer.iterate()
    assert reader.sample() == {}
    reader.iterate()
    assert reader.sample() == {}

    metrics.register(Label('herp', 'derp'))
    writer.iterate()
    assert reader.sample() == {}
    reader.iterate()
    assert reader.sample() == {'herp': 'derp'}


def test_metric_sample():
  metrics = Metrics()
  sampler = MetricSampler(metrics)
  assert sampler.sample() == {}
  sampler.iterate()
  assert sampler.sample() == {}
  metrics.register(Label('herp', 'derp'))
  assert sampler.sample() == {}
  sampler.iterate()
  assert sampler.sample() == {'herp': 'derp'}
