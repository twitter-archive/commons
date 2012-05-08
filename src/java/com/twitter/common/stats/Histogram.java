package com.twitter.common.stats;

/**
 * An interface for Histogram
 */
public interface Histogram {

  /**
   * Add an entry into the histogram
   *
   * @param x the value to insert
   */
  void add(long x);

  /**
   * Clear the histogram
   */
  void clear();

  /**
   * Return the current quantiles of the histogram
   *
   * @param quantiles the list of quantiles that you want to compute
   */
  long[] getQuantiles(double[] quantiles);
}
