package com.twitter.common.stats;

/**
 * Helper class containing only static methods
 */
public final class Histograms {

  private Histograms() {
    /* Disable */
  }

  /**
   * Helper method that return an array of quantiles
   * @param h the histogram to query
   * @param quantiles an array of double representing the quantiles
   * @return the array of computed quantiles
   */
  public static long[] extractQuantiles(Histogram h, double[] quantiles) {
    long[] results = new long[quantiles.length];
    for (int i = 0; i < results.length; i++) {
      double q = quantiles[i];
      results[i] = h.getQuantile(q);
    }
    return results;
  }
}
