// =================================================================================================
// Copyright 2013 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.metrics;

import org.junit.Test;
import org.pantsbuild.junit.annotations.TestParallel;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.Precision;
import com.twitter.common.util.testing.FakeClock;

import static org.junit.Assert.assertEquals;

import static com.twitter.common.metrics.Histogram.DEFAULT_QUANTILES;
import static com.twitter.common.stats.WindowedApproxHistogram.DEFAULT_MAX_MEMORY;
import static com.twitter.common.stats.WindowedApproxHistogram.DEFAULT_SLICES;
import static com.twitter.common.stats.WindowedApproxHistogram.DEFAULT_WINDOW;

/**
 * Tests Histogram.
 */
@TestParallel
public class HistogramTest {

  private String name = "hist";
  private static final Precision DEFAULT_PRECISION = new Precision(0.02, 100 * 1000);
  private static final double ERROR = DEFAULT_PRECISION.getEpsilon() * DEFAULT_PRECISION.getN();

  @Test
  public void testEmptyHistogram() {
    Histogram hist = new Histogram(name);

    Snapshot sample = hist.snapshot();
    assertEquals(0L, sample.min());
    assertEquals(0L, sample.max());
    assertEquals(0L, sample.count());
    assertEquals(0L, sample.sum());
    assertEquals(0.0, sample.avg(), 0.1);
    long[] expected = {0L, 0L, 0L, 0L, 0L, 0L};
    checkQuantiles(expected, sample, ERROR);
  }

  @Test
  public void testHistogram() {
    int n = 10000;
    FakeClock clock = new FakeClock();
    Precision precision = new Precision(0.001, n);
    int slices = 2;
    HistogramInterface hist = new Histogram(name, Amount.of(1L, Time.MINUTES), slices, null,
        precision, DEFAULT_QUANTILES, clock);
    double error = precision.getEpsilon() * precision.getN() * slices;

    for (int i = 1; i <= n; ++i) {
      hist.add(i);
    }
    clock.advance(Amount.of(31L, Time.SECONDS));

    Snapshot sample = hist.snapshot();
    assertEquals(1L, sample.min());
    assertEquals((long) n, sample.max());
    assertEquals((long) n, sample.count());
    assertEquals((long) (n * (n + 1) / 2), sample.sum());
    assertEquals(n * (n + 1) / (2.0 * n), sample.avg(), 0.1);
    long[] expected = new long[DEFAULT_QUANTILES.length];
    for (int i = 0; i < DEFAULT_QUANTILES.length; i++) {
      expected[i] = (long) (DEFAULT_QUANTILES[i] * n);
    }
    checkQuantiles(expected, sample, error);
  }

  @Test
  public void testHistogramWithMemoryConstraints() {
    int n = 10000;
    FakeClock clock = new FakeClock();
    int slices = 2;
    double error = DEFAULT_PRECISION.getEpsilon() * n * slices;
    // We suppose here that 32KB is enough for the default precision of (slices + 1) histograms
    HistogramInterface hist = new Histogram(name, Amount.of(1L, Time.MINUTES), slices,
        Amount.of(32L, Data.KB), null, DEFAULT_QUANTILES, clock);

    for (int i = 1; i <= n; ++i) {
      hist.add(i);
    }
    clock.advance(Amount.of(31L, Time.SECONDS));

    Snapshot sample = hist.snapshot();
    assertEquals(1L, sample.min());
    assertEquals((long) n, sample.max());
    assertEquals((long) n, sample.count());
    assertEquals((long) (n * (n + 1) / 2), sample.sum());
    assertEquals(n * (n + 1) / (2.0 * n), sample.avg(), 0.1);

    long[] expected = new long[DEFAULT_QUANTILES.length];
    for (int i = 0; i < DEFAULT_QUANTILES.length; i++) {
      expected[i] = (long) (DEFAULT_QUANTILES[i] * n);
    }
    checkQuantiles(expected, sample, error);
  }

  @Test
  public void testNegative() {
    FakeClock clock = new FakeClock();

    HistogramInterface hist = new Histogram(name, DEFAULT_WINDOW,
        DEFAULT_SLICES, DEFAULT_MAX_MEMORY, null, DEFAULT_QUANTILES, clock);

    int[] data = new int[201];
    for (int i = 0; i < data.length; ++i) {
      data[i] = -100 + i;
    }
    for (int x : data) {
      hist.add(x);
    }
    clock.advance(Amount.of(31L, Time.SECONDS));

    Snapshot sample = hist.snapshot();
    assertEquals(-100L, sample.min());
    assertEquals(100L, sample.max());
    assertEquals((long) data.length, sample.count());
    assertEquals(0L, sample.sum());
    assertEquals(0.0, sample.avg(), 0.1);

    long[] expected = new long[DEFAULT_QUANTILES.length];
    for (int i = 0; i < DEFAULT_QUANTILES.length; i++) {
      int idx = (int) (DEFAULT_QUANTILES[i] * data.length);
      expected[i] = data[idx];
    }

    checkQuantiles(expected, sample, ERROR);
  }

  private void checkQuantiles(long[] expectedQuantiles, Snapshot sample, double e) {
    assertEquals(expectedQuantiles.length, DEFAULT_QUANTILES.length);

    Percentile[] measureQuantiles = sample.percentiles();
    for (int i = 0; i < expectedQuantiles.length; i++) {
      double expected = expectedQuantiles[i];
      double measure = measureQuantiles[i].getValue();
      assertEquals(expected, measure, e);
    }
  }
}
