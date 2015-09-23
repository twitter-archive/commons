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
    registerGauge(gauge);
  }

  @Override
  public synchronized <T extends Number> Gauge<T> registerGauge(Gauge<T> gauge) {
    String key = gauge.getName();
    checkNameCollision(key);
    gauges.put(key, gauge);
    return gauge;
  }

  @Override
  public synchronized boolean unregister(Gauge<?> gauge) {
    String key = gauge.getName();
    return gauges.remove(key) != null;
  }

  @Override
  public Counter registerCounter(String name) {
    return createCounter(name);
  }

  @Override
  public synchronized Counter createCounter(final String name) {
    final LongAdder adder = new LongAdder();
    checkNameCollision(name);
    counters.put(name, adder);
    return new Counter() {
      public String getName() { return name; }
      public void increment() {
        adder.increment();
      }
      public void add(long n) {
        adder.add(n);
      }
    };
  }

  @Override
  public synchronized boolean unregister(Counter counter) {
    String key = counter.getName();
    return counters.remove(key) != null;
  }

  @Override
  public HistogramInterface createHistogram(String name) {
    return registerHistogram(new Histogram(name));
  }

  @Override
  public synchronized HistogramInterface registerHistogram(HistogramInterface histogram) {
    String key = histogram.getName();
    checkNameCollision(key);
    histograms.put(key, histogram);
    return histogram;
  }

  @Override
  public synchronized boolean unregister(HistogramInterface histogram) {
    String key = histogram.getName();
    return histograms.remove(key) != null;
  }

  @Override
  public synchronized boolean unregister(String metricName) {
    return gauges.remove(metricName) != null
        || counters.remove(metricName) != null
        || histograms.remove(metricName) != null;
  }


  @Override
  public Map<String, Number> sampleGauges() {
    ImmutableMap.Builder<String, Number> samples = ImmutableMap.builder();
    for (Map.Entry<String, Gauge<?>> metric : gauges.entrySet()) {
      Gauge<?> gauge = metric.getValue();
      samples.put(metric.getKey(), gauge.read());
    }
    return samples.build();
  }

  @Override
    public Map<String, Number> sampleCounters() {
    ImmutableMap.Builder<String, Number> samples = ImmutableMap.builder();
    for (Map.Entry<String, LongAdder> metric : counters.entrySet()) {
      samples.put(metric.getKey(), metric.getValue().sum());
    }
    return samples.build();
  }

  @Override
  public Map<String, Snapshot> sampleHistograms() {
    ImmutableMap.Builder<String, Snapshot> samples = ImmutableMap.builder();
    for (HistogramInterface h: histograms.values()) {
      Snapshot snapshot = h.snapshot();
      samples.put(h.getName(), snapshot);
    }
    return samples.build();
  }

  @Override
  public Map<String, Number> sample() {
    ImmutableMap.Builder<String, Number> samples = ImmutableMap.builder();
    samples.putAll(sampleGauges());
    samples.putAll(sampleCounters());
    for (Map.Entry<String, Snapshot> entry : sampleHistograms().entrySet()) {
      String name = entry.getKey();
      Snapshot snapshot = entry.getValue();
      samples.put(named(name, "count"), snapshot.count());
      samples.put(named(name, "sum"), snapshot.sum());
      samples.put(named(name, "avg"), snapshot.avg());
      samples.put(named(name, "min"), snapshot.min());
      samples.put(named(name, "max"), snapshot.max());
      samples.put(named(name, "stddev"), snapshot.stddev());
      for (Percentile p: snapshot.percentiles()) {
        String percentileName = named(name, gaugeName(p.getQuantile()));
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
