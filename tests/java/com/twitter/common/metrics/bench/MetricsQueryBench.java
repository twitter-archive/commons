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
import java.util.concurrent.atomic.AtomicLong;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.metrics.Counter;
import com.twitter.common.metrics.Histogram;
import com.twitter.common.metrics.Metrics;
import com.twitter.common.metrics.WindowedApproxHistogram;

/**
 * This bench tests different sorts of queries.
 */
public class MetricsQueryBench extends SimpleBenchmark {

  private static int N = 100 * 1000;
  private static int RANGE = 15 * 1000;
  private static double[] quantiles = {0.5, 0.9, 0.95, 0.99};
  private Metrics metrics;
  private Random rnd;
  private WindowedApproxHistogram bigHist;
  private WindowedApproxHistogram smallHist;

  @Override
  protected void setUp() {
    metrics = Metrics.createDetached();
    rnd = new Random(1);

    for (int i = 0; i < 1000; i++) {
      metrics.createCounter("counter-" + i).increment();
      Histogram h = new Histogram("hist-" + i, metrics);
      for (int j=0; j < N; j++) {
        h.add(rnd.nextInt(RANGE));
      }
    }

    smallHist = new WindowedApproxHistogram();
    bigHist = new WindowedApproxHistogram(Amount.of(1L, Data.MB));
    for (int j=0; j < N; j++) {
      smallHist.add(rnd.nextInt(RANGE));
      bigHist.add(rnd.nextInt(RANGE));
    }
  }

  /**
   * Realistic bench of a querying 1000 counters and 1000 histograms
   */
  public void timeQueryMetrics(int n) {
    long x;
    while(n != 0) {
      metrics.sample();
      n--;
    }
  }

  public void timeQueryHistograms(int n) {
    long[] res;
    while(n != 0) {
      res = smallHist.getQuantiles(quantiles);
      n--;
    }
  }

  public void timeQueryBigHistograms(int n) {
    long[] res;
    while(n != 0) {
      res = bigHist.getQuantiles(quantiles);
      n--;
    }
  }
}
