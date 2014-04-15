// =================================================================================================
// Copyright 2013 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.twitter.common.base.MorePreconditions;

/**
 * A metric registry that is a 'child' of another metric registry.
 */
class ScopedRegistry implements MetricRegistry {

  @VisibleForTesting public static final String DEFAULT_SCOPE_DELIMITER = ".";

  private final MetricRegistry parent;
  private final String name;

  /**
   * Creates a new scoped metric registry.
   * When a gauge is registered with this registry, it will be passed directly to the parent,
   * with the scope name applied to the gauge name.
   *
   * @param name Name of this scope.
   * @param parent Parent scope to register gauges with.
   */
  @VisibleForTesting
  ScopedRegistry(MetricRegistry parent, String name) {
    this.parent = Preconditions.checkNotNull(parent);
    this.name = MorePreconditions.checkNotBlank(name);
  }

  @Override
  public MetricRegistry scope(String scopeName) {
    return new ScopedRegistry(this, scopeName);
  }

  private String scopeName(String metricName) {
    return name + DEFAULT_SCOPE_DELIMITER + metricName;
  }

  @Override
  public <T extends Number> void register(final Gauge<T> gauge) {
    registerGauge(gauge);
  }

  @Override
  public <T extends Number> Gauge<T> registerGauge(final Gauge<T> gauge) {
    final String scopedName = scopeName(gauge.getName());
    return parent.registerGauge(new AbstractGauge<T>(scopedName) {
      @Override public T read() { return gauge.read(); }
    });
  }

  @Override
  public boolean unregister(Gauge gauge) {
    return parent.unregister(gauge);
  }

  @Override
  public Counter createCounter(String counterName) {
    return parent.createCounter(scopeName(counterName));
  }

  @Override
  public Counter registerCounter(String counterName) {
    return createCounter(counterName);
  }

  @Override
  public boolean unregister(Counter counter) {
    return parent.unregister(counter);
  }


  @Override
  public HistogramInterface createHistogram(String histogramName) {
    return parent.createHistogram(scopeName(histogramName));
  }

  @Override
  public HistogramInterface registerHistogram(final HistogramInterface histogram) {
    HistogramInterface h = new HistogramInterface() {
      @Override public String getName() { return scopeName(histogram.getName()); }
      @Override public void clear() { histogram.clear(); }
      @Override public void add(long n) { histogram.add(n); }
      @Override public Snapshot snapshot() { return histogram.snapshot(); }
    };
    parent.registerHistogram(h);
    return h;
  }

  @Override
  public boolean unregister(HistogramInterface histogram) {
    return parent.unregister(histogram);
  }

  @Override
  public boolean unregister(String metricName) {
    return parent.unregister(scopeName(metricName));
  }
}
