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


from twitter.common.lang import Compatibility


# Duck-typing helpers
def gaugelike(obj):
  return hasattr(obj, 'read') and callable(obj.read)

def namable(obj):
  return hasattr(obj, 'name') and callable(obj.name)

def namablegauge(obj):
  return gaugelike(obj) and namable(obj)


# Typed gauges.
class Gauge(object):
  """
    A readable gauge that exports a value.
  """
  def __init__(self, value):
    self._value = value

  def read(self):
    return self._value


class NamedGauge(Gauge):
  """
    Named gauge (gauge that exports name() method.)
  """
  def __init__(self, name, value=None):
    if not isinstance(name, str):
      raise TypeError('NamedGauge must be named by a string, got %s' % type(name))
    self._name = name
    Gauge.__init__(self, value)

  def name(self):
    return self._name


class MutableGauge(Gauge):
  """
    Mutable gauge.
  """
  def __init__(self, value=None):
    import threading
    self._lock = threading.Lock()
    Gauge.__init__(self, value)

  def read(self):
    with self.lock():
      return self._value

  def write(self, value):
    with self.lock():
      self._value = value
      return self._value

  def lock(self):
    return self._lock


class Label(NamedGauge):
  """
    A generic immutable key-value Gauge.  (Not specifically strings, but that's
    the intention.)
  """
  def __init__(self, name, value):
    NamedGauge.__init__(self, name, value)


class LambdaGauge(NamedGauge):
  def __init__(self, name, fn):
    import threading
    if not callable(fn):
      raise TypeError("A LambdaGauge must be supplied with a callable, got %s" % type(fn))
    NamedGauge.__init__(self, name, fn)
    self._lock = threading.Lock()

  def read(self):
    with self._lock:
      return self._value()


class MutatorGauge(NamedGauge, MutableGauge):
  def __init__(self, name, value=None):
    NamedGauge.__init__(self, name)
    MutableGauge.__init__(self, value)


class AtomicGauge(NamedGauge, MutableGauge):
  """
    Something akin to AtomicLong. Basically a MutableGauge but with
    atomic add, increment, decrement.
  """
  def __init__(self, name, initial_value=0):
    if not isinstance(initial_value, Compatibility.integer):
      raise TypeError('AtomicGauge must be initialized with an integer.')
    NamedGauge.__init__(self, name)
    MutableGauge.__init__(self, initial_value)

  def add(self, delta):
    """
      Add delta to metric and return updated metric.
    """
    if not isinstance(delta, Compatibility.integer):
      raise TypeError('AtomicGauge.add must be called with an integer.')
    with self.lock():
      self._value += delta
      return self._value

  def increment(self):
    """
      Increment metric and return updated metric.
    """
    return self.add(1)

  def decrement(self):
    """
      Decrement metric and return updated metric.
    """
    return self.add(-1)
