// =================================================================================================
// Copyright 2011 Twitter, Inc.
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

package com.twitter.common.stats;

import com.google.common.collect.Sets;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;
import com.twitter.common.util.testing.FakeClock;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests the PipelineStats class.
 *
 * @author William Farner
 */
public class PipelineStatsTest {

  private Clock clock = new FakeClock();
  private PipelineStats stats;

  @Before
  public void setUp() {
    stats = new PipelineStats("test", Sets.newHashSet("a", "b", "c"), clock, Time.MILLISECONDS);
  }

  @Test
  public void testEmptyFlow() {
    PipelineStats.Snapshot pipeline = stats.newSnapshot();
    pipeline.end();

    checkSample("a", 0, 0);
    checkSample("b", 0, 0);
    checkSample("c", 0, 0);
    checkSample("full", 1, 0);
  }

  @Test
  public void testSimpleFlow() throws Exception {
    PipelineStats.Snapshot pipeline = stats.newSnapshot();
    pipeline.start("a");
    clock.waitFor(10);
    pipeline.start("b");
    clock.waitFor(20);
    pipeline.start("c");
    clock.waitFor(30);
    pipeline.end();

    checkSample("a", 1, 10);
    checkSample("b", 1, 20);
    checkSample("c", 1, 30);
    checkSample("full", 1, 60);
  }

  @Test
  public void testEarlyExit() throws Exception {
    PipelineStats.Snapshot pipeline = stats.newSnapshot();
    pipeline.start("a");
    clock.waitFor(10);
    pipeline.start("b");
    clock.waitFor(20);
    pipeline.end();

    checkSample("a", 1, 10);
    checkSample("b", 1, 20);
    checkSample("full", 1, 30);
  }

  @Test
  public void testDuplicatedStages() throws Exception {
    PipelineStats.Snapshot pipeline = stats.newSnapshot();
    pipeline.start("a");
    clock.waitFor(10);
    pipeline.start("b");
    clock.waitFor(20);
    pipeline.start("b");
    clock.waitFor(10);
    pipeline.start("b");
    clock.waitFor(50);
    pipeline.start("c");
    clock.waitFor(30);
    pipeline.start("c");
    clock.waitFor(70);
    pipeline.end();

    checkSample("a", 1, 10);
    checkSample("b", 3, 80);
    checkSample("c", 2, 100);
    checkSample("full", 1, 190);
  }

  @Test
  public void testSimultaneousSnapshots() throws Exception {
    PipelineStats.Snapshot pipeline1 = stats.newSnapshot();
    PipelineStats.Snapshot pipeline2 = stats.newSnapshot();
    pipeline1.start("a");
    clock.waitFor(10);
    pipeline2.start("a");
    pipeline1.start("b");
    clock.waitFor(20);
    pipeline2.start("b");
    clock.waitFor(10);
    pipeline2.start("c");
    clock.waitFor(10);
    pipeline2.end();

    // Only pipeline2 was recorded, so we should not see pipeline1 in the time series yet.
    checkSample("a", 1, 20);
    checkSample("b", 1, 10);
    checkSample("c", 1, 10);
    checkSample("full", 1, 40);

    pipeline1.start("c");
    clock.waitFor(30);
    pipeline1.end();

    // The current sample will now be the sum of pipeline1 and pipeline2.
    checkSample("a", 2, 30);
    checkSample("b", 2, 50);
    checkSample("c", 2, 40);
    checkSample("full", 2, 120);
  }

  private void checkSample(String stage, long events, long latency) {
    AtomicLong eventsCounter = stats.getStatsForStage(stage).getEventCounter();
    AtomicLong latencyCounter = stats.getStatsForStage(stage).getTotalCounter();

    assertThat(eventsCounter.get(), is(events));
    assertThat(latencyCounter.get(), is(latency));
  }
}
