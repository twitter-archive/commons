package com.twitter.common.metrics;

import java.util.Map;

/**
 * A listener that receives updated metric samples.
 */
public interface MetricListener {

  /**
   * Notifies the listener of updated samples.
   *
   * @param samples Updated samples.  Samples associated with the same metric will use a consistent
   *     key, and keys may be added or removed over the lifetime of the listener.
   */
  void updateStats(Map<String, Number> samples);
}
