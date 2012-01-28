package com.twitter.common.metrics;

import java.util.Map;

/**
 * A provider of metric samples.
 */
public interface MetricProvider {

  /**
   * Obtains a snapshot of all available metric values.
   *
   * @return Metric samples.
   */
  Map<String, Number> sample();
}
