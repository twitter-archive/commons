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

import com.twitter.common.metrics.Counter;
import com.twitter.common.metrics.HistogramInterface;
import com.twitter.common.metrics.Metrics;
import com.twitter.common.stats.WindowedApproxHistogram;
import com.twitter.common.stats.WindowedStatistics;

/**
 * Bench different sorts of insertion in Metrics
 */
public class MetricsInsertionBench extends SimpleBenchmark {

  private static final int INPUT_RANGE = 15000;
  private Metrics metrics;
  private Counter counter;
  private HistogramInterface h;
  private WindowedApproxHistogram wh;
  private WindowedStatistics ws;

  @Override
  protected void setUp() {
    metrics = Metrics.createDetached();
    counter = metrics.createCounter("counter");
    h = metrics.createHistogram("histogram");
    wh = new WindowedApproxHistogram();
    ws = new WindowedStatistics();
  }

  public void timeIncrementCounter(int n) {
    int i = n;
    while (i != 0) {
      counter.increment();
      i--;
    }
  }

  public void timeAddValueInHistogram(int n) {
    int i = n;
    while (i != 0) {
      h.add(1);
      i--;
    }
  }

  public void timeAddValueInWinHistogram(int n) {
    int i = n;
    while (i != 0) {
      wh.add(1);
      i--;
    }
  }

  public void timeAddValueInWinStats(int n) {
    int i = n;
    while (i != 0) {
      ws.accumulate(1);
      i--;
    }
  }
}
