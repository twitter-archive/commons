package com.twitter.common.metrics;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.junit.annotations.TestParallel;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.stats.ApproximateHistogram;
import com.twitter.common.stats.Precision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests Histogram.
 */
@TestParallel
public class HistogramTest {

  private String name = "hist";
  private Metrics metrics;
  private static final double ERROR = ApproximateHistogram.DEFAULT_PRECISION.getEpsilon()
      * ApproximateHistogram.DEFAULT_PRECISION.getN();

  @Before
  public void setUp() {
    metrics = Metrics.createDetached();
  }

  @Test
  public void testEmptyHistogram() {
    Histogram hist = new Histogram(name, metrics);

    Map<String, Number> sample = metrics.sample();
    assertEquals(0L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "min"));
    assertEquals(0L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "max"));
    assertEquals(0L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "count"));
    assertEquals(0L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "sum"));
    assertEquals(0L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "avg"));
    long[] expected = {0L, 0L, 0L, 0L, 0L, 0L};
    checkQuantiles(expected, sample, ERROR);
  }

  @Test
  public void testHistogram() {
    int n = 10000;
    Histogram hist = new Histogram(name, new Precision(0.001, n), metrics);

    for (int i = 1; i <= n; ++i) {
      hist.add(i);
    }

    Map<String, Number> sample = metrics.sample();
    assertEquals(1L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "min"));
    assertEquals((long) n, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "max"));
    assertEquals((long) n, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "count"));
    assertEquals((long) (n * (n + 1) / 2),
        sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "sum"));
    assertEquals((long) n / 2, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "avg"));
    long[] expected = new long[Histogram.DEFAULT_QUANTILES.length];
    for (int i = 0; i < Histogram.DEFAULT_QUANTILES.length; i++) {
      expected[i] = (long) (Histogram.DEFAULT_QUANTILES[i] * n);
    }
    checkQuantiles(expected, sample, 0.001 * n);
  }

  @Test
  public void testHistogramWithMemoryConstraints() {
    int n = 10000;
    Histogram hist = new Histogram(name, Amount.of(8L, Data.KB), metrics);

    for (int i = 1; i <= n; ++i) {
      hist.add(i);
    }

    Map<String, Number> sample = metrics.sample();
    assertEquals(1L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "min"));
    assertEquals((long) n, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "max"));
    assertEquals((long) n, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "count"));
    assertEquals((long) (n * (n + 1) / 2),
        sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "sum"));
    assertEquals((long) n / 2, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "avg"));

    double errorInPercent = 0.0;
    for (double q : Histogram.DEFAULT_QUANTILES) {
      String gName = name + ScopedMetrics.SCOPE_DELIMITER + Histogram.gaugeName(q);
      errorInPercent += Math.abs(((q * n) - sample.get(gName).doubleValue()) / (q * n));
    }
    assertTrue(errorInPercent / Histogram.DEFAULT_QUANTILES.length < 0.01);
  }

  @Test
  public void testNegative() {
    Histogram hist = new Histogram(name, metrics);
    int[] data = new int[200];
    for (int i = 0; i < data.length; ++i) {
      data[i] = -100 + i;
    }
    for (int x : data) {
      hist.add(x);
    }

    Map<String, Number> sample = metrics.sample();
    assertEquals(-100L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "min"));
    assertEquals(99L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "max"));
    assertEquals(200L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "count"));
    assertEquals(-100L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "sum"));
    assertEquals(0L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "avg"));

    long[] expected = new long[Histogram.DEFAULT_QUANTILES.length];
    for (int i = 0; i < Histogram.DEFAULT_QUANTILES.length; i++) {
      int idx = (int) (Histogram.DEFAULT_QUANTILES[i] * data.length);
      expected[i] = data[idx];
    }

    checkQuantiles(expected, sample, ERROR);
  }

  private void checkQuantiles(long[] expectedQuantiles, Map<String, Number> sample, double e) {
    assertEquals(expectedQuantiles.length, Histogram.DEFAULT_QUANTILES.length);

    for (int i = 0; i < Histogram.DEFAULT_QUANTILES.length; i++) {
      double q = Histogram.DEFAULT_QUANTILES[i];
      String gName = Histogram.gaugeName(q);
      assertEquals((double) expectedQuantiles[i],
          sample.get(name + ScopedMetrics.SCOPE_DELIMITER + gName).doubleValue(), e);
    }
  }
}
