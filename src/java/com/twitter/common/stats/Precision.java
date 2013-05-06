package com.twitter.common.stats;

import com.google.common.base.Preconditions;

/**
 * Precision expresses the maximum epsilon tolerated for a typical size of input
 * e.g.: Precision(0.01, 1000) express that we tolerate a error of 1% for 1000 entries
 *       it means that max difference between the real quantile and the estimate one is
 *       error = 0.01*1000 = 10
 *       For an entry like (1 to 1000), q(0.5) will be [490 <= x <= 510] (real q(0.5) = 500)
 */
public class Precision {
  private final double epsilon;
  private final int n;

  /**
   * Create a Precision instance representing a precision per number of entries
   *
   * @param epsilon is the maximum error tolerated
   * @param n size of the data set
   */
  public Precision(double epsilon, int n) {
    Preconditions.checkArgument(0.0 < epsilon, "Epsilon must be positive!");
    Preconditions.checkArgument(1 < n, "N (expected number of elements) must be greater than 1!");

    this.epsilon = epsilon;
    this.n = n;
  }

  public double getEpsilon() {
    return epsilon;
  }

  public int getN() {
    return n;
  }
}
