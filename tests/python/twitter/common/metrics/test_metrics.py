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

from twitter.common.quantity import Amount, Time, Data
from twitter.common.metrics import Label, MutatorGauge
from twitter.common.metrics import (
    CompoundMetrics,
    Observable,
    RootMetrics)
from twitter.common.metrics.metrics import Metrics


def test_root_metrics_singleton():
  rm = RootMetrics()
  rm2 = RootMetrics()
  assert id(rm) == id(rm2)


def test_basic_registration_and_clear():
  lb = Label('ping', 'pong')
  rm = RootMetrics()
  rm.register(lb)
  assert rm.sample() == {'ping': 'pong'}
  rm.clear()
  assert rm.sample() == {}


def test_nontrivial_gauges():
  for label_value in ['a', 0, 2.5, [1,2,"3"], {'a': 'b'}, {'c': None}, False]:
    lb = Label('ping', label_value)
    rm = RootMetrics()
    rm.register(lb)
    assert rm.sample() == {'ping': label_value}
    rm.clear()
    assert rm.sample() == {}


def test_basic_scoping():
  lb = Label('ping', 'pong')
  rm = RootMetrics()
  rm.register(lb)
  rm.scope('bing').register(lb)
  assert rm.sample() == { 'ping': 'pong', 'bing.ping': 'pong' }
  rm.clear()


def test_scoped_registration_uses_references():
  mg = MutatorGauge('name', 'brian')
  rm = RootMetrics()
  rm.scope('earth').register(mg)
  rm.scope('pluto').register(mg)
  assert rm.sample() == { 'earth.name': 'brian', 'pluto.name': 'brian' }
  mg.write('zargon')
  assert rm.sample() == { 'earth.name': 'zargon', 'pluto.name': 'zargon' }
  rm.clear()


def test_register_string():
  rm = RootMetrics()
  hello_gauge = rm.register('hello')
  assert rm.sample() == { 'hello': None }
  hello_gauge.write('poop')
  assert rm.sample() == { 'hello': 'poop' }
  rm.clear()


def test_nested_scopes():
  rm = RootMetrics()
  mg = rm.scope('a').scope('b').scope('c').register('123')
  mg.write(Amount(1, Time.MILLISECONDS))
  assert rm.sample() == {'a.b.c.123': '1 ms'}
  rm.clear()


def test_bad_scope_names():
  rm = RootMetrics()
  my_scope = rm.scope('my_scope')
  with pytest.raises(TypeError):
    my_scope.scope(None)
  with pytest.raises(TypeError):
    my_scope.scope({})
  with pytest.raises(TypeError):
    my_scope.scope(123)
  with pytest.raises(TypeError):
    my_scope.scope(RootMetrics)


def test_compound_metrics():
  metrics1 = Metrics()
  metrics2 = Metrics()

  metrics1.register(Label('value', 'first'))
  metrics2.register(Label('value', 'second'))
  assert CompoundMetrics(metrics1, metrics2).sample() == {'value': 'second'}

  metrics1.register(Label('other', 'third'))
  assert CompoundMetrics(metrics1, metrics2).sample() == {
      'value': 'second', 'other': 'third'}


def test_observable():
  class Derp(Observable):
    def __init__(self):
      self.metrics.register(Label('value', 'derp value'))
  metrics = Metrics()
  metrics.register_observable('derpspace', Derp())
  assert metrics.sample() == {'derpspace.value': 'derp value'}
