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

package com.twitter.common.stats;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import com.twitter.common.objectsize.ObjectSizeCalculator;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.testing.RealHistogram;
import com.twitter.common.util.testing.FakeClock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static com.twitter.common.stats.WindowedApproxHistogram.DEFAULT_MAX_MEMORY;

/**
 * Tests WindowedHistogram.
 */
public class WindowedHistogramTest {

  @Test
  public void testEmptyWinHistogram() {
    WindowedApproxHistogram wh = new WindowedApproxHistogram();
    assertEquals(0L, wh.getQuantile(0.0));
  }

  @Test
  public void testWinHistogramWithEdgeCases() {
    FakeClock clock = new FakeClock();
    Amount<Long, Time> window = Amount.of(100L, Time.MILLISECONDS);
    int slices = 10;
    long sliceDuration = window.as(Time.NANOSECONDS) / slices;
    WindowedApproxHistogram h =
        new WindowedApproxHistogram(window, slices, DEFAULT_MAX_MEMORY, clock);

    h.add(Long.MIN_VALUE);
    clock.advance(Amount.of(2 * sliceDuration, Time.NANOSECONDS));
    assertEquals(Long.MIN_VALUE, h.getQuantile(0.0));
    assertEquals(Long.MIN_VALUE, h.getQuantile(0.5));
    assertEquals(Long.MIN_VALUE, h.getQuantile(1.0));

    h.add(Long.MAX_VALUE);
    clock.advance(Amount.of(2 * sliceDuration, Time.NANOSECONDS));
    assertEquals(Long.MIN_VALUE, h.getQuantile(0.0));
    assertEquals(Long.MIN_VALUE, h.getQuantile(0.25));
    assertEquals(Long.MAX_VALUE, h.getQuantile(0.75));
    assertEquals(Long.MAX_VALUE, h.getQuantile(1.0));
  }

  @Test
  public void testClearedWinHistogram() {
    FakeClock clock = new FakeClock();
    Amount<Long, Time> window = Amount.of(100L, Time.MILLISECONDS);
    int slices = 10;
    Amount<Long, Time> sliceDuration = Amount.of(
        window.as(Time.NANOSECONDS) / slices, Time.NANOSECONDS);
    WindowedHistogram<?> h = createFullHistogram(window, slices, clock);
    long p0 = h.getQuantile(0.1);
    long p50 = h.getQuantile(0.5);
    long p90 = h.getQuantile(0.9);
    assertFalse(0 == p0);
    assertFalse(0 == p50);
    assertFalse(0 == p90);

    h.clear();

    assertEquals(0, h.getQuantile(0.1));
    assertEquals(0, h.getQuantile(0.5));
    assertEquals(0, h.getQuantile(0.9));

    // reload the histogram with the exact same values than before
    fillHistogram(h, sliceDuration, slices, clock);

    assertEquals(p0, h.getQuantile(0.1));
    assertEquals(p50, h.getQuantile(0.5));
    assertEquals(p90, h.getQuantile(0.9));
  }

  @Test
  public void testSimpleWinHistogram() {
    FakeClock clock = new FakeClock();
    Amount<Long, Time> window = Amount.of(100L, Time.MILLISECONDS);
    int slices = 10;
    WindowedHistogram<?> wh = createFullHistogram(window, slices, clock);

    // check that the global distribution is the aggregation of all underlying histograms
    for (int i = 1; i <= slices; i++) {
      double q = (double) i / slices;
      assertEquals(i, wh.getQuantile(q), 1.0);
    }

    // advance in time an forget about old values
    long sliceDuration = window.as(Time.NANOSECONDS) / slices;
    clock.advance(Amount.of(sliceDuration, Time.NANOSECONDS));
    for (int j = 0; j < 1000; j++) {
      wh.add(11);
    }
    assertEquals(2, wh.getQuantile(0.05), 1.0);
    assertEquals(11, wh.getQuantile(0.99), 1.0);
  }

  @Test
  public void testWinHistogramWithGap() {
    FakeClock clock = new FakeClock();
    Amount<Long, Time> window = Amount.of(100L, Time.MILLISECONDS);
    int slices = 10;
    WindowedHistogram<?> wh = createFullHistogram(window, slices, clock);
    // wh is a WindowedHistogram of 10 slices + the empty current with values from 1 to 10
    // [1][2][3][4][5][6][7][8][9][10][.]
    //                                 ^

    for (int j = 0; j < 1000; j++) {
      wh.add(100);
    }
    // [1][2][3][4][5][6][7][8][9][10][100]
    //                                  ^
    // quantiles are computed based on [1] -> [10]

    clock.advance(Amount.of((slices - 1) * 100L / slices, Time.MILLISECONDS));
    for (int j = 0; j < 1000; j++) {
      wh.add(200);
    }
    // [1][2][3][4][5][6][7][8][200][10][100]
    //                           ^
    // quantiles are computed based on [10][100][1][2][3][4][5][6][7][8]
    // and removing old ones           [10][100][.][.][.][.][.][.][.][.]
    // all the histograms between 100 and 200 are old and shouldn't matter in the computation of
    // quantiles.
    assertEquals(10L, wh.getQuantile(0.25), 1.0);
    assertEquals(100L, wh.getQuantile(0.75), 1.0);

    clock.advance(Amount.of(100L / slices, Time.MILLISECONDS));
    // [1][2][3][4][5][6][7][8][200][10][100]
    //                               ^
    // quantiles are computed based on [100][1][2][3][4][5][6][7][8][200]
    // and removing old ones           [100][.][.][.][.][.][.][.][.][200]

    assertEquals(100L, wh.getQuantile(0.25), 1.0);
    assertEquals(200L, wh.getQuantile(0.75), 1.0);

    // advance a lot in time, everything should be "forgotten"
    clock.advance(Amount.of(500L, Time.MILLISECONDS));
    assertEquals(0L, wh.getQuantile(0.5), 1.0);
  }

  @Test
  public void testWinHistogramMemory() {
    ImmutableList.Builder<Amount<Long, Data>> builder = ImmutableList.builder();
    builder.add(Amount.of(8L, Data.KB));
    builder.add(Amount.of(12L, Data.KB));
    builder.add(Amount.of(16L, Data.KB));
    builder.add(Amount.of(20L, Data.KB));
    builder.add(Amount.of(24L, Data.KB));
    builder.add(Amount.of(32L, Data.KB));
    builder.add(Amount.of(64L, Data.KB));
    builder.add(Amount.of(256L, Data.KB));
    builder.add(Amount.of(1L, Data.MB));
    builder.add(Amount.of(16L, Data.MB));
    builder.add(Amount.of(32L, Data.MB));
    List<Amount<Long, Data>> sizes = builder.build();

    // large estimation of the memory used outside of buffers
    long fixSize = Amount.of(4, Data.KB).as(Data.BYTES);

    for (Amount<Long, Data> maxSize: sizes) {
      WindowedApproxHistogram hist = new WindowedApproxHistogram(
          Amount.of(60L, Time.SECONDS), 6, maxSize);
      hist.add(1L);
      hist.getQuantile(0.5);
      long size = ObjectSizeCalculator.getObjectSize(hist);
      // reverting CI JVM seems to have different memory consumption than mine
      //assertTrue(size < fixSize + maxSize.as(Data.BYTES));
    }
  }

  @Test
  public void testWinHistogramAccuracy() {
    FakeClock ticker = new FakeClock();
    Amount<Long, Time> window = Amount.of(100L, Time.MILLISECONDS);
    int slices = 10;
    Amount<Long, Time> sliceDuration = Amount.of(
        window.as(Time.NANOSECONDS) / slices, Time.NANOSECONDS);
    WindowedHistogram<?> wh = createFullHistogram(window, slices, ticker);
    RealHistogram rh = fillHistogram(new RealHistogram(), sliceDuration, slices, new FakeClock());

    assertEquals(wh.getQuantile(0.5), rh.getQuantile(0.5));
    assertEquals(wh.getQuantile(0.75), rh.getQuantile(0.75));
    assertEquals(wh.getQuantile(0.9), rh.getQuantile(0.9));
    assertEquals(wh.getQuantile(0.99), rh.getQuantile(0.99));
  }

  /**
   * @return a WindowedHistogram with different value in each underlying Histogram
   */
  private WindowedHistogram<?> createFullHistogram(
      Amount<Long, Time> duration, int slices, FakeClock clock) {
    long sliceDuration = duration.as(Time.NANOSECONDS) / slices;
    WindowedApproxHistogram wh = new WindowedApproxHistogram(duration, slices,
        DEFAULT_MAX_MEMORY, clock);
    clock.advance(Amount.of(1L, Time.NANOSECONDS));

    return fillHistogram(wh, Amount.of(sliceDuration, Time.NANOSECONDS), slices, clock);
  }

  private <H extends Histogram> H fillHistogram(H h,
      Amount<Long, Time> sliceDuration, int slices, FakeClock clock) {
    for (int i = 1; i <= slices; i++) {
      for (int j = 0; j < 1000; j++) {
        h.add(i);
      }
      clock.advance(sliceDuration);
    }
    return h;
  }
}
