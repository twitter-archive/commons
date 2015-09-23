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

import java.util.Random;

import com.google.caliper.SimpleBenchmark;

import com.twitter.common.metrics.Counter;
import com.twitter.common.metrics.HistogramInterface;
import com.twitter.common.metrics.Metrics;
import com.twitter.common.stats.ApproximateHistogram;
import com.twitter.common.stats.WindowedApproxHistogram;

/**
 * Bench memory allocated for each component of Metrics
 */
public class MetricsCreationBench extends SimpleBenchmark {

  private static final int INPUT_RANGE = 15000;
  private Metrics metrics;
  private Random rnd;

  @Override
  protected void setUp() {
    metrics = Metrics.createDetached();
    rnd = new Random(1);
  }

  public void timeCreatingCounter(int n) {
    Counter counter;
    int i = n;
    while (i != 0) {
      counter = metrics.createCounter("counter" + i);
      counter.increment();
      i--;
    }
  }

  /**
   * An Histogram creates 13 gauges
   * "count", "sum", "avg", "min", "max"
   * "p25", "p50", "p75", "p90", "p95", "p99", "p999", "p9999"
   */
  public void timeCreatingHistogram(int n) {
    HistogramInterface h;
    int i = n;
    while (i != 0) {
      h = metrics.createHistogram("histogram");
      h.add(1);
      i--;
    }
  }

  /**
   * ApproximateHistogram is the underlying datastructure backing the Histogram primitive.
   */
  public void timeCreatingApproxHistogram(int n) {
    ApproximateHistogram h;
    int i = n;
    while (i != 0) {
      h = new ApproximateHistogram();
      h.add(1);
      i--;
    }
  }

  /**
   * WindowedHistogram is serie of ApproximateHistograms in sliding window.
   */
  public void timeCreatingWindowedHistogram(int n) {
    WindowedApproxHistogram h;
    int i = n;
    while (i != 0) {
      h = new WindowedApproxHistogram();
      h.add(1);
      i--;
    }
  }
}
