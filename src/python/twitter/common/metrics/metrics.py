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


from twitter.common.lang import Compatibility, Singleton
from .gauge import (
  Gauge,
  MutatorGauge,
  NamedGauge,
  namablegauge)


class Observable(object):
  """
    A trait providing a metric namespace for an object.

    Classes should mix-in Observable and register metrics against self.metrics.

    Application owners can then register observable objects into a metric
    space or the root metrics, e.g. via

    >>> RootMetrics().register_observable('object_namespace', my_object)

  """
  @property
  def metrics(self):
    """
      Returns a Metric namespace for this object.
    """
    if not hasattr(self, '_observable_metrics'):
      self._observable_metrics = Metrics()
    return self._observable_metrics


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

  def unregister(self, name):
    """
      Unregister a name from the registry.
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

  @classmethod
  def coerce_value(cls, value):
    if isinstance(value, Compatibility.numeric + Compatibility.string + (bool,)):
      return value
    elif value is None:
      return value
    elif isinstance(value, list):
      return [cls.coerce_value(v) for v in value]
    elif isinstance(value, dict):
      return dict((cls.coerce_value(k), cls.coerce_value(v)) for (k, v) in value.items())
    else:
      return str(value)

  @classmethod
  def coerce_metric(cls, metric_tuple):
    name, value = metric_tuple
    try:
      return (name, cls.coerce_value(value.read()))
    except ValueError:
      return None

  def __init__(self):
    self._metrics = {}
    self._children = {}

  def scope(self, name):
    if not isinstance(name, Compatibility.string):
      raise TypeError('Scope names must be strings, got: %s' % type(name))
    if name not in self._children:
      self._children[name] = Metrics()
    return self._children[name]

  def register_observable(self, name, observable):
    if not isinstance(name, Compatibility.string):
      raise TypeError('Scope names must be strings, got: %s' % type(name))
    if not isinstance(observable, Observable):
      raise TypeError('observable must be an Observable, got: %s' % type(observable))
    self._children[name] = observable.metrics

  def unregister_observable(self, name):
    if not isinstance(name, Compatibility.string):
      raise TypeError('Unregister takes a string name!')
    return self._children.pop(name, None)

  def register(self, gauge):
    if isinstance(gauge, Compatibility.string):
      gauge = MutatorGauge(gauge)
    if not isinstance(gauge, NamedGauge) and not namablegauge(gauge):
      raise TypeError('Must register either a string or a Gauge-like object! Got %s' % gauge)
    self._metrics[gauge.name()] = gauge
    return gauge

  def unregister(self, name):
    if not isinstance(name, Compatibility.string):
      raise TypeError('Unregister takes a string name!')
    return self._metrics.pop(name, None)

  @classmethod
  def sample_name(cls, scope_name, sample_name):
    return '.'.join([scope_name, sample_name])

  def sample(self):
    samples = dict(filter(None, map(self.coerce_metric, self._metrics.items())))
    for scope_name, scope in self._children.items():
      samples.update((self.sample_name(scope_name, sample_name), sample_value)
                     for (sample_name, sample_value) in scope.sample().items())
    return samples


class CompoundMetrics(MetricProvider):
  def __init__(self, *providers):
    if not all(isinstance(provider, MetricProvider) for provider in providers):
      raise TypeError('CompoundMetrics must take a collection of MetricProviders')
    self._providers = providers

  def sample(self):
    root_sample = {}
    for provider in self._providers:
      root_sample.update(provider.sample())
    return root_sample


class MemoizedMetrics(MetricProvider):
  def __init__(self, provider):
    if not isinstance(provider, MetricProvider):
      raise TypeError('MemoizedMetrics must take a MetricProvider')
    self._provider = provider
    self._sample = {}

  def sample(self):
    self._sample = self._provider.sample()
    return self._sample

  @property
  def memoized_sample(self):
    return self._sample


class RootMetrics(Metrics, Singleton):
  """
    Root singleton instance of the metrics.
  """

  _INIT = False

  def __init__(self):
    if not RootMetrics._INIT:
      Metrics.__init__(self)
      RootMetrics._INIT = True

  # For testing.
  def clear(self):
    Metrics.__init__(self)
