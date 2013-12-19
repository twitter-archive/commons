package com.twitter.common.stats;

/**
 * An interface for Histogram
 */
public interface Histogram {

  /**
   * Add an entry into the histogram.
   * @param x the value to insert.
   */
  void add(long x);

  /**
   * Clear the histogram.
   */
  void clear();

  /**
   * Return the current quantile of the histogram.
   * @param quantile value to compute.
   */
  long getQuantile(double quantile);

  /**
   * Return the quantiles of the histogram.
   * @param quantiles array of values to compute.
   */
  long[] getQuantiles(double[] quantiles);
}
