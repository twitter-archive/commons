package com.twitter.common.metrics;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.stats.Precision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests Histogram.
 */
public class HistogramTest {

  private static final double EPS = 1e-8;
  private String name = "hist";
  private Metrics metrics;

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
    long[] expected = {0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L};
    checkQuantiles(expected, sample);
  }


  @Test
  public void testHistogram() {
    int n = 10000;
    Histogram hist = new Histogram(name, new Precision(0.0001, n), metrics);

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
    checkQuantiles(expected, sample);
  }

  @Test
  public void testHistogramWithMemoryConstraints() {
    int n = 10000;
    Histogram hist = new Histogram(name, Amount.of(4L, Data.KB), metrics);

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
  public void testavgPrecision() {
    int n =  1000 * 1000;
    Histogram hist = new Histogram(name, Amount.of(4L, Data.KB), metrics);

    long sum = 0L;
    for (int i = 0; i <= 2 * n; ++i) {
      sum += i;
      hist.add(i);
    }

    Map<String, Number> sample = metrics.sample();
  }

  @Test
  public void testNegative() {
    Histogram hist = new Histogram(name, metrics);

    for (int i = -100; i <= 100; ++i) {
      hist.add(i);
    }

    Map<String, Number> sample = metrics.sample();
    assertEquals(-100L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "min"));
    assertEquals(100L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "max"));
    assertEquals(201L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "count"));
    assertEquals(0L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "sum"));
    assertEquals(0L, sample.get(name + ScopedMetrics.SCOPE_DELIMITER + "avg"));

    long[] expected = {-51L, -1L, 49L, 79L, 89L, 97L, 99L, 99L};
    checkQuantiles(expected, sample);
  }

  private void checkQuantiles(long[] expectedQuantiles, Map<String, Number> sample) {
    assertEquals(expectedQuantiles.length, Histogram.DEFAULT_QUANTILES.length);

    for (int i = 0; i < Histogram.DEFAULT_QUANTILES.length; i++) {
      double q = Histogram.DEFAULT_QUANTILES[i];
      String gName = Histogram.gaugeName(q);
      assertEquals(expectedQuantiles[i], sample.get(name + ScopedMetrics.SCOPE_DELIMITER + gName));
    }
  }
}
