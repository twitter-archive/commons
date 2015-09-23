package com.twitter.common.metrics;

/**
 * An exception thrown if a metric is defined with the same name as the previous metric
 */
public class MetricCollisionException extends IllegalArgumentException {
  public MetricCollisionException(String s) { super(s); }
}
