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
import com.twitter.common.metrics.Metrics;

/**
 * Bench different Counter.increment under different contented scenario.
 */
public class MetricsContendedInsertionBench extends SimpleBenchmark {

  private Metrics metrics;

  @Override
  protected void setUp() {
    metrics = Metrics.createDetached();
  }

  public void timeIncrementCounter(final int n) {
    final Counter counter = metrics.createCounter("counter1");
    contend(1, new Runnable() {
      @Override
      public void run() {
        int i = n;
        while (i != 0) {
          counter.increment();
          i--;
        }
      }
    });
  }

  public void timeContendedIncrementCounter(final int n) {
    final Counter counter = metrics.createCounter("counter16");
    contend(16, new Runnable() {
      @Override
      public void run() {
        int i = n;
        while (i != 0) {
          counter.increment();
          i--;
        }
      }
    });
  }

  private void contend(int nThreads, Runnable action) {
    Thread[] threads = new Thread[nThreads];
    for (int i = 0; i < nThreads; i++) {
      threads[i] = new Thread(action);
      threads[i].start();
    }
    for (int i = 0; i < nThreads; i++) {
      try {
        threads[i].join();
      } catch (InterruptedException e) { /*ignore*/ }
    }
  }
}
