package com.twitter.common.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A registry that maintains a collection of metrics.
 */
public interface MetricRegistry {

  /**
   * Returns or creates a sub-scope of this metric registry.
   *
   * @param name Name for the sub-scope.
   * @return A possibly-new metric registry, whose metrics will be 'children' of this scope.
   */
  MetricRegistry scope(String name);

  /**
   * Registers a new gauge.
   *
   * @param gauge Gauge to register.
   * @param <T> Number type of the gauge's values.
   */
  <T extends Number> void register(Gauge<T> gauge);

  /**
   * Creates a gauge and returns an {@link AtomicLong} that can be modified to update the value.
   *
   * @param name Name to associate with the gauge.
   * @return Handle to modify the gauge value.
   */
  AtomicLong registerLong(String name);
}
