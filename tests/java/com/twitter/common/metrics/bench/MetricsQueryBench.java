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

package com.twitter.common.metrics.bench;

import java.util.Map;

import com.google.caliper.SimpleBenchmark;

import com.twitter.common.metrics.Histogram;
import com.twitter.common.metrics.HistogramInterface;
import com.twitter.common.metrics.Metrics;
import com.twitter.common.metrics.Snapshot;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.WindowedApproxHistogram;
import com.twitter.common.stats.WindowedStatistics;
import com.twitter.common.util.testing.FakeClock;

/**
 * This bench tests different sorts of queries.
 */
public class MetricsQueryBench extends SimpleBenchmark {

  private static final int N = 100 * 1000;
  private static final int RANGE = 15 * 1000;
  private static final double[] QUANTILES = {0.5, 0.9, 0.95, 0.99};
  private Metrics metrics;
  private HistogramInterface hist;
  private WindowedApproxHistogram approxHist;
  private WindowedStatistics winStats;

  @Override
  protected void setUp() {
    metrics = Metrics.createDetached();
    FakeClock clock = new FakeClock();
    Amount<Long, Time> window = WindowedApproxHistogram.DEFAULT_WINDOW;
    int slices = WindowedApproxHistogram.DEFAULT_SLICES;
    Amount<Long, Time> delta = Amount.of(window.as(Time.MILLISECONDS) / N, Time.MILLISECONDS);
    Amount<Long, Data> maxMem = WindowedApproxHistogram.DEFAULT_MAX_MEMORY;

    for (int i = 0; i < 10; i++) {
      metrics.createCounter("counter-" + i).increment();
      HistogramInterface h = new Histogram("hist-" + i, window, slices, maxMem, null,
        Histogram.DEFAULT_QUANTILES, clock, metrics);
      for (int j = 0; j < N; j++) {
        h.add(j);
        clock.advance(delta);
      }
    }

    // Initialize Histograms and fill them with values (in every buckets for windowed ones)
    hist = new Histogram("hist", window, slices, maxMem, null, Histogram.DEFAULT_QUANTILES, clock);
    approxHist = new WindowedApproxHistogram(window, slices, maxMem, clock);
    winStats = new WindowedStatistics(window, slices, clock);
    for (int j = 0; j < N; j++) {
      hist.add(j);
      approxHist.add(j);
      winStats.accumulate(j);
      clock.advance(delta);
    }
  }

  /**
   * Realistic bench of a querying 1000 counters and 1000 histograms
   */
  public void timeQueryMetrics(int n) {
    Map<String, Number> res;
    int i = n;
    while (i != 0) {
      res = metrics.sample();
      i--;
    }
  }

  public void timeSnapshotHistogram(int n) {
    Snapshot res;
    int i = n;
    while (i != 0) {
      res = hist.snapshot();
      i--;
    }
  }

  public void timeQueryWindowedApproxHistogram(int n) {
    long[] res;
    int i = n;
    while (i != 0) {
      res = approxHist.getQuantiles(QUANTILES);
      i--;
    }
  }

  public void timeQueryWindowedStatistics(int n) {
    long x;
    double y;
    int i = n;
    while (i != 0) {
      winStats.refresh();
      x = winStats.max();
      x = winStats.min();
      x = winStats.populationSize();
      x = winStats.range();
      x = winStats.sum();
      y = winStats.mean();
      y = winStats.standardDeviation();
      i--;
    }
  }
}
