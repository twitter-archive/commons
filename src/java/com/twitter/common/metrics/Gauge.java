package com.twitter.common.metrics;

/**
 * A metric that has a name and a variable number value.
 *
 * @param <T> Value type.
 */
public interface Gauge<T extends Number> {

  /**
   * Gets the name of this stat. For sake of convention, variable names should be alphanumeric, and
   * use underscores.
   *
   * @return The variable name.
   */
  String getName();

  /**
   * Reads the latest value of the metric.
   * Must never return {@code null}.
   *
   * @return The metric value.
   */
  T read();
}
