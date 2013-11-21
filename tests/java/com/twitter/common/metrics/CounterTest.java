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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests metric Counter behavior.
 */
public class CounterTest {

  private static final long INCREMENTS = 1000;
  private Metrics metrics;

  @Before
  public void setUp() {
    metrics = Metrics.createDetached();
  }

  @Test
  public void testCounterSingleThread() {
    String name = "counter1Thread";
    Counter counter = metrics.createCounter(name);
    for (int i = 0; i < INCREMENTS; i++) {
      counter.increment();
    }
    Number value = metrics.sample().get(name);
    assertEquals("Single thread increment immediately seen", value.longValue(), INCREMENTS);
  }

  @Test
  public void testLongAdderMultiThread() {
    String name = "longAdder16Thread";
    final Counter counter = metrics.createCounter(name);
    int threadNumber = 16;
    Thread[] threads = new Thread[threadNumber];
    for (int i = 0; i < threadNumber; i++) {
      threads[i] = new Thread() {
        @Override
        public void run() {
          for (int i = 0; i < INCREMENTS; i++) {
            counter.increment();
          }
        }
      };
      threads[i].start();
    }
    for (int i = 0; i < threadNumber; i++) {
      try {
        threads[i].join();
      } catch (InterruptedException e) { /* ignore */ }
    }

    // Pause to let the value propagate
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) { /* ignore */ }

    Number value = metrics.sample().get(name);
    assertEquals("Concurrent increments seen after some time",
      value.longValue(), threadNumber * INCREMENTS);
  }
}
