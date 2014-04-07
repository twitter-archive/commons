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

  /**
   * Obtains a snapshot of all available gauges.
   *
   * @return Metric samples.
   */
  Map<String, Number> sampleGauges();

  /**
   * Obtains a snapshot of all available counters.
   *
   * @return Metric samples.
   */
  Map<String, Number> sampleCounters();

  /**
   * Obtains a snapshot of all available histograms.
   *
   * @return Metric samples.
   */
  Map<String, Snapshot> sampleHistograms();
}
