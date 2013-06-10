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

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import com.twitter.common.objectsize.ObjectSizeCalculator;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.testing.FakeTicker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.twitter.common.metrics.WindowedApproxHistogram.DEFAULT_MAX_MEMORY;

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
    FakeTicker ticker = new FakeTicker();
    Amount<Long, Time> window = Amount.of(100L, Time.MILLISECONDS);
    int slices = 10;
    long sliceDuration = window.as(Time.NANOSECONDS) / slices;
    WindowedApproxHistogram h =
        new WindowedApproxHistogram(window, slices, DEFAULT_MAX_MEMORY, ticker);

    h.add(Long.MIN_VALUE);
    ticker.advance(Amount.of(2 * sliceDuration, Time.NANOSECONDS));
    assertEquals(Long.MIN_VALUE, h.getQuantile(0.0));
    assertEquals(Long.MIN_VALUE, h.getQuantile(0.5));
    assertEquals(Long.MIN_VALUE, h.getQuantile(1.0));

    h.add(Long.MAX_VALUE);
    ticker.advance(Amount.of(2 * sliceDuration, Time.NANOSECONDS));
    assertEquals(Long.MIN_VALUE, h.getQuantile(0.0));
    assertEquals(Long.MIN_VALUE, h.getQuantile(0.25));
    assertEquals(Long.MAX_VALUE, h.getQuantile(0.75));
    assertEquals(Long.MAX_VALUE, h.getQuantile(1.0));
  }

  @Test
  public void testSimpleWinHistogram() {
    FakeTicker ticker = new FakeTicker();
    Amount<Long, Time> window = Amount.of(100L, Time.MILLISECONDS);
    int slices = 10;
    WindowedHistogram<?> wh = createFullHistogram(window, slices, ticker);

    // check that the global distribution is the aggregation of all underlying histograms
    for (int i = 1; i <= slices; i++) {
      double q = (double) i / slices;
      assertEquals(i, wh.getQuantile(q), 1.0);
    }

    // advance in time an forget about old values
    long sliceDuration = window.as(Time.NANOSECONDS) / slices;
    ticker.advance(Amount.of(sliceDuration, Time.NANOSECONDS));
    for (int j = 0; j < 1000; j++) {
      wh.add(11);
    }
    assertEquals(2, wh.getQuantile(0.05), 1.0);
    assertEquals(11, wh.getQuantile(0.99), 1.0);
  }

  @Test
  public void testWinHistogramWithGap() {
    FakeTicker ticker = new FakeTicker();
    Amount<Long, Time> window = Amount.of(100L, Time.MILLISECONDS);
    int slices = 10;
    WindowedHistogram<?> wh = createFullHistogram(window, slices, ticker);
    // wh is a WindowedHistogram of 10 slices + the empty current with values from 1 to 10
    // [1][2][3][4][5][6][7][8][9][10][.]
    //                                 ^

    for (int j = 0; j < 1000; j++) {
      wh.add(100);
    }
    // [1][2][3][4][5][6][7][8][9][10][100]
    //                                  ^
    // quantiles are computed based on [1] -> [10]

    ticker.advance(Amount.of((slices - 1) * 100L / slices, Time.MILLISECONDS));
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

    ticker.advance(Amount.of(100L / slices, Time.MILLISECONDS));
    // [1][2][3][4][5][6][7][8][200][10][100]
    //                               ^
    // quantiles are computed based on [100][1][2][3][4][5][6][7][8][200]
    // and removing old ones           [100][.][.][.][.][.][.][.][.][200]

    assertEquals(100L, wh.getQuantile(0.25), 1.0);
    assertEquals(200L, wh.getQuantile(0.75), 1.0);

    // advance a lot in time, everything should be "forgotten"
    ticker.advance(Amount.of(500L, Time.MILLISECONDS));
    assertEquals(0L, wh.getQuantile(0.5), 1.0);
  }

  @Test
  public void testWinHistogramMemory() {
    ImmutableList.Builder<Amount<Long, Data>> builder = ImmutableList.builder();
    builder.add(Amount.of(8L, Data.KB));
    builder.add(Amount.of(16L, Data.KB));
    builder.add(Amount.of(32L, Data.KB));
    builder.add(Amount.of(64L, Data.KB));
    builder.add(Amount.of(256L, Data.KB));
    builder.add(Amount.of(1L, Data.MB));
    builder.add(Amount.of(16L, Data.MB));
    builder.add(Amount.of(32L, Data.MB));
    List<Amount<Long, Data>> sizes = builder.build();

    for (Amount<Long, Data> maxSize: sizes) {
      WindowedApproxHistogram hist = new WindowedApproxHistogram(
          Amount.of(60L, Time.SECONDS), 6, maxSize);
      hist.add(1L);
      hist.getQuantile(0.5);
      long size = ObjectSizeCalculator.getObjectSize(hist);
      assertTrue(size < maxSize.as(Data.BYTES));
    }
  }

  /**
   * @return a WindowedHistogram with different value in each underlying Histogram
   */
  private WindowedHistogram<?> createFullHistogram(
      Amount<Long, Time> duration, int slices, FakeTicker ticker) {
    long sliceDuration = duration.as(Time.NANOSECONDS) / slices;
    WindowedApproxHistogram wh = new WindowedApproxHistogram(duration, slices,
        DEFAULT_MAX_MEMORY, ticker);
    ticker.advance(Amount.of(1L, Time.NANOSECONDS));

    for (int i = 1; i <= slices; i++) {
      for (int j = 0; j < 1000; j++) {
        wh.add(i);
      }
      ticker.advance(Amount.of(sliceDuration, Time.NANOSECONDS));
    }
    return wh;
  }
}
