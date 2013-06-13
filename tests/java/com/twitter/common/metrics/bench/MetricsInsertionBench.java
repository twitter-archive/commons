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
import com.twitter.common.metrics.Histogram;
import com.twitter.common.metrics.Metrics;
import com.twitter.common.metrics.WindowedApproxHistogram;

/**
 * Bench different sorts of insertion in Metrics
 */
public class MetricsInsertionBench extends SimpleBenchmark {

  private static final int INPUT_RANGE = 15000;
  private Metrics metrics;
  private Counter counter;
  private Histogram h;
  private WindowedApproxHistogram wh;

  @Override
  protected void setUp() {
    metrics = Metrics.createDetached();
    counter = metrics.registerCounter("counter");
    h = new Histogram("histogram", metrics);
    wh = new WindowedApproxHistogram();
  }

  public void timeIncrementCounter(int n) {
    while(n != 0) {
      counter.increment();
      n--;
    }
  }

  public void timeAddValueInHistogram(int n) {
    while(n != 0) {
      h.add(1);
      n--;
    }
  }

  public void timeAddValueInWinHistogram(int n) {
    while(n != 0) {
      wh.add(1);
      n--;
    }
  }

}
