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

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import com.twitter.jsr166e.LongAdder;

/**
 * Root metric registry.
 */
public final class Metrics implements MetricRegistry, MetricProvider {
  private static final Metrics ROOT = new Metrics();
  private static final Object NAME_LOCK = new Object();

  private final Map<String, Gauge<?>> gauges = Maps.newConcurrentMap();
  private final Map<String, LongAdder> counters = Maps.newConcurrentMap();
  private final Map<String, HistogramInterface> histograms = Maps.newConcurrentMap();

  private Metrics() { }

  private void checkNameCollision(String key) {
    if (gauges.containsKey(key) || counters.containsKey(key) || counters.containsKey(key)) {
      throw new MetricCollisionException(
          "A metric with the name " + key + " has already been defined");
    }
  }

  /**
   * Create a new Metrics detached from the static root.
   *
   * @return the detached metric registry.
   */
  public static Metrics createDetached() {
    return new Metrics();
  }

  /**
   * Returns a handle to the root metric registry.
   *
   * @return Root metric registry.
   */
  public static Metrics root() {
    return ROOT;
  }

  @Override
  public MetricRegistry scope(String name) {
    return new ScopedRegistry(this, name);
  }

  @Override
  public <T extends Number> void register(Gauge<T> gauge) {
    String key = gauge.getName();
    synchronized (NAME_LOCK) {
      checkNameCollision(key);
      gauges.put(key, gauge);
    }
  }

  @Override
  public Counter registerCounter(String name) {
    return createCounter(name);
  }

  @Override
  public Counter createCounter(String name) {
    final LongAdder adder = new LongAdder();
    synchronized (NAME_LOCK) {
      checkNameCollision(name);
      counters.put(name, adder);
    }
    return new Counter() {
      public void increment() {
        adder.increment();
      }
      public void add(long n) {
        adder.add(n);
      }
    };
  }

  @Override
  public HistogramInterface createHistogram(String name) {
    return registerHistogram(new Histogram(name));
  }

  @Override
  public HistogramInterface registerHistogram(HistogramInterface histogram) {
    String key = histogram.getName();
    synchronized (NAME_LOCK) {
      checkNameCollision(key);
      histograms.put(key, histogram);
    }
    return histogram;
  }

  @Override
  public Map<String, Number> sample() {
    ImmutableMap.Builder<String, Number> samples = ImmutableMap.builder();
    // Collect all gauges
    for (Map.Entry<String, Gauge<?>> metric : gauges.entrySet()) {
      Gauge<?> gauge = metric.getValue();
      samples.put(metric.getKey(), gauge.read());
    }
    // Collect all counters
    for (Map.Entry<String, LongAdder> metric : counters.entrySet()) {
      samples.put(metric.getKey(), metric.getValue().sum());
    }
    // Collect all statistics of histograms
    for (HistogramInterface h : histograms.values()) {
      Snapshot snapshot = h.snapshot();
      samples.put(named(h.getName(), "count"), snapshot.count());
      samples.put(named(h.getName(), "sum"), snapshot.sum());
      samples.put(named(h.getName(), "avg"), snapshot.avg());
      samples.put(named(h.getName(), "min"), snapshot.min());
      samples.put(named(h.getName(), "max"), snapshot.max());
      samples.put(named(h.getName(), "stddev"), snapshot.stddev());
      for (Percentile p : snapshot.percentiles()) {
        String percentileName = named(h.getName(), gaugeName(p.getQuantile()));
        samples.put(percentileName, p.getValue());
      }
    }
    return samples.build();
  }

  private static String named(String histogramName, String statName) {
    return histogramName + ScopedRegistry.DEFAULT_SCOPE_DELIMITER + statName;
  }

  private static String gaugeName(double quantile) {
    String gname = "p" + (int) (quantile * 10000);
    if (3 < gname.length() && "00".equals(gname.substring(3))) {
      gname = gname.substring(0, 3);
    }
    return gname;
  }
}
