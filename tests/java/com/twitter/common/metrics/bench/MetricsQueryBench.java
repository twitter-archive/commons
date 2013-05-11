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

/**
 * This bench tests different sorts of queries.
 */
public class MetricsQueryBench extends SimpleBenchmark {

  private static int N = 100 * 1000;
  private static int RANGE = 15 * 1000;
  private Metrics metrics;
  private Random rnd;

  @Override
  protected void setUp() {
    metrics = Metrics.createDetached();
    rnd = new Random(1);
  }

  public void timeQueryCounters(int n) {
    for (int i=0; i < 52; i++) {
      Counter counter = metrics.registerCounter("counter" + i);
      counter.increment();
    }
    while(n != 0) {
      metrics.sample();
      n--;
    }
  }

  public void timeQueryHistograms(int n) {
    for (int i=0; i < 4; i++) {
      // Each histogram registers 13 gauges
      Histogram h = new Histogram("histogram" + i, metrics);
      for (int j=0; j < N; j++) {
        h.add(rnd.nextInt(RANGE));
      }
    }
    while(n != 0) {
      metrics.sample();
      n--;
    }
  }

    public void timeQueryBigHistograms(int n) {
    for (int i=0; i < 4; i++) {
      // Each histogram registers 13 gauges
      Histogram h = new Histogram("histogram" + i, Amount.of(1L, Data.MB), metrics);
      for (int j=0; j < N; j++) {
        h.add(rnd.nextInt(RANGE));
      }
    }
    while(n != 0) {
      metrics.sample();
      n--;
    }
  }

}
