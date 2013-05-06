package com.twitter.common.metrics;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Root metric registry.
 */
public class Metrics implements MetricRegistry, MetricProvider {

  private static final Metrics ROOT = new Metrics();

  private final Map<String, Gauge> metrics = Maps.newConcurrentMap();


  @VisibleForTesting
  Metrics() {
    // Package private.
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
    return new ScopedMetrics(name, this);
  }

  @Override
  public <T extends Number> void register(Gauge<T> gauge) {
    // TODO(wfarner): Define a policy for handling collisions.
    metrics.put(gauge.getName(), gauge);
  }

  @Override
  public AtomicLong registerLong(String name) {
    final AtomicLong gauge = new AtomicLong();
    register(new AbstractGauge<Long>(name) {
      @Override public Long read() {
        return gauge.get();
      }
    });
    return gauge;
  }

  @Override
  public Map<String, Number> sample() {
    ImmutableMap.Builder<String, Number> samples = ImmutableMap.builder();
    for (Map.Entry<String, Gauge> metric : metrics.entrySet()) {
      samples.put(metric.getKey(), metric.getValue().read());
    }
    return samples.build();
  }
}
