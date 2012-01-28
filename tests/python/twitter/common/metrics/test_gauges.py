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
from twitter.common.metrics import (
  Label,
  AtomicGauge,
  MutatorGauge,

  gaugelike,
  namable,
  namablegauge,
)

def test_basic_immutable_gauge():
  lb = Label('a', 'b')
  assert lb.name() == 'a', 'name should be properly initialized'
  assert lb.read() == 'b', 'label should be properly set'

  # should handle a variety of types
  lb = Label('a', None)
  assert lb.read() == None

  lb = Label('a', [1,2,3])
  assert lb.read() == [1,2,3]

  lb = Label('b', {})
  assert lb.read() == {}

def test_basic_mutable_gauge():
  mg = MutatorGauge('a')
  assert mg.name() == 'a'
  assert mg.read() == None
  mg = MutatorGauge('a', 'b')
  assert mg.name() == 'a'
  assert mg.read() == 'b'
  mg.write('c')
  assert mg.name() == 'a'
  assert mg.read() == 'c'
  mg.write(None)
  assert mg.read() == None

def test_atomic_gauge():
  ag = AtomicGauge('a')
  assert ag.name() == 'a'
  assert ag.read() == 0
  assert ag.add(-2) == -2
  ag = AtomicGauge('a')
  assert ag.decrement() == -1

def test_atomic_gauge_types():
  with pytest.raises(TypeError):
    ag = AtomicGauge('a', None)
  with pytest.raises(TypeError):
    ag = AtomicGauge('a', 'hello')
  ag = AtomicGauge('a', 23)
  with pytest.raises(TypeError):
    ag.add(None)
  with pytest.raises(TypeError):
    ag.add('hello')

def test_named_gauge_types():
  with pytest.raises(TypeError):
    ag = AtomicGauge(0)
  with pytest.raises(TypeError):
    ag = AtomicGauge(None)
  with pytest.raises(TypeError):
    lb = Label(None, 3)
  with pytest.raises(TypeError):
    mg = MutatorGauge({})

def test_duck_typing():
  class Anonymous(object):
    pass
  A = Anonymous()
  A.name = lambda: 'anonymous'
  assert namable(A), 'any object with a name method should be considered namable'
  A = Anonymous()
  A.read = lambda: 5
  assert gaugelike(A), 'any object with a read method should be considered gaugelike'
  A.name = lambda: 'anonymous'
  assert namablegauge(A), 'any object with read/name method shoudl be considered namablegauge'

  A.name = 'anonymous'
  assert not namablegauge(A)
  assert not namable(A)
  assert gaugelike(A)

  A.read = '5'
  assert not gaugelike(A)
