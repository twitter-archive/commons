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

import com.google.caliper.SimpleBenchmark;

import java.util.Random;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.metrics.Counter;
import com.twitter.common.metrics.Histogram;
import com.twitter.common.metrics.Metrics;
import com.twitter.common.metrics.WindowedApproxHistogram;
import com.twitter.common.stats.ApproximateHistogram;

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
    while(n != 0) {
      counter = metrics.registerCounter("counter");
      counter.increment();
      n--;
    }
  }

  /**
   * An Histogram creates 13 gauges
   * "count", "sum", "avg", "min", "max"
   * "p25", "p50", "p75", "p90", "p95", "p99", "p999", "p9999"
   */
  public void timeCreatingHistogram(int n) {
    Histogram h;
    while(n != 0) {
      h = new Histogram("histogram", metrics);
      h.add(rnd.nextInt(INPUT_RANGE));
      n--;
    }
  }

  /**
   * ApproximateHistogram is the underlying datastructure backing the Histogram primitive.
   */
  public void timeCreatingApproxHistogram(int n) {
    ApproximateHistogram h;
    while(n != 0) {
      h = new ApproximateHistogram();
      h.add(rnd.nextInt(INPUT_RANGE));
      n--;
    }
  }

  /**
   * WindowedHistogram is serie of ApproximateHistograms in sliding window.
   */
  public void timeCreatingWindowedHistogram(int n) {
    WindowedApproxHistogram h;
    while(n != 0) {
      h = new WindowedApproxHistogram();
      h.add(rnd.nextInt(INPUT_RANGE));
      n--;
    }
  }
}
