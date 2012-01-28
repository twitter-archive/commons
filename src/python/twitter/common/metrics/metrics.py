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

from gauge import Gauge, MutatorGauge, NamedGauge, namablegauge

class MetricProvider(object):
  def sample(self):
    """
      Returns a dictionary
        string (metric) => sample (number)
    """
    raise NotImplementedError


class MetricRegistry(object):
  def scope(self, name):
    """
      Returns a (potentially memoized) child scope with a given name.
    """
    raise NotImplementedError

  def register(self, gauge):
    """
      Register a gauge (mapper from name => sample) with this registry.
    """
    raise NotImplementedError

  def mutator(self, name):
    """
      Return a mutator function of the gauge associated with name.
    """
    raise NotImplementedError


class Metrics(MetricRegistry, MetricProvider):
  """
    Metric collector.
  """

  class Error(Exception): pass

  def __init__(self):
    self._metrics = {}
    self._children = {}

  def scope(self, name):
    if not isinstance(name, basestring):
      raise TypeError('Scope names must be strings, got: %s' % type(name))
    if name not in self._children:
      self._children[name] = Metrics()
    return self._children[name]

  def register(self, gauge):
    if isinstance(gauge, basestring):
      gauge = MutatorGauge(gauge)
    if not isinstance(gauge, NamedGauge) and not namablegauge(gauge):
      raise Metrics.Error('Must register either a string or a Gauge-like object! Got %s' % gauge)
    self._metrics[gauge.name()] = gauge
    return gauge

  def sample(self, sample_prefix=''):
    samples = {}
    for name, metric in self._metrics.iteritems():
      samples[sample_prefix + name] = str(metric.read())
    for scope_name in self._children:
      samples.update(self.scope(scope_name)
                         .sample(sample_prefix=sample_prefix + scope_name + '.'))
    return samples


class RootMetrics(Metrics):
  """
    Root singleton instance of the metrics.
  """

  _SINGLETON = None
  _INIT = False

  def __new__(cls, *args, **kwargs):
    if cls._SINGLETON is None:
      cls._SINGLETON = super(RootMetrics, cls).__new__(cls, *args, **kwargs)
    return cls._SINGLETON

  def __init__(self):
    if not RootMetrics._INIT:
      Metrics.__init__(self)
      RootMetrics._INIT = True

  # For testing.
  def clear(self):
    Metrics.__init__(self)
