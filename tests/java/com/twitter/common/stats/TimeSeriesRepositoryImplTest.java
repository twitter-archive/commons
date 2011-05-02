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

import com.google.common.collect.Lists;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.testing.FakeClock;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.easymock.EasyMock.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class TimeSeriesRepositoryImplTest {

  private static final Amount<Long, Time> RETENTION_PERIOD = Amount.of(10L, Time.MINUTES);
  private static final Amount<Long, Time> SAMPLE_PERIOD = Amount.of(1L, Time.SECONDS);

  private TimeSeriesRepositoryImpl repo;
  private FakeClock clock;

  private IMocksControl control;

  @Before
  public void setUp() {
    repo = new TimeSeriesRepositoryImpl(SAMPLE_PERIOD, RETENTION_PERIOD);
    clock = new FakeClock();

    control = createStrictControl();
  }

  @After
  public void after() {
    Stats.flush();
    control.verify();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSamplesInOrder() {
    Stat<Integer> statA = control.createMock(Stat.class);
    Stat<Integer> statB = control.createMock(Stat.class);
    Stat<Integer> statC = control.createMock(Stat.class);
    Stat<Integer> statD = control.createMock(Stat.class);

    expect(statB.getName()).andReturn("statB");
    expectLastCall().atLeastOnce();
    expect(statA.getName()).andReturn("statA");
    expectLastCall().atLeastOnce();
    expect(statC.getName()).andReturn("statC");
    expectLastCall().atLeastOnce();
    expect(statD.getName()).andReturn("statD");
    expectLastCall().atLeastOnce();

    expect(statB.getName()).andReturn("statB");
    expectLastCall().atLeastOnce();
    expect(statB.read()).andReturn(1);
    expect(statA.getName()).andReturn("statA");
    expectLastCall().atLeastOnce();
    expect(statA.read()).andReturn(2);
    expect(statC.getName()).andReturn("statC");
    expectLastCall().atLeastOnce();
    expect(statC.read()).andReturn(3);
    expect(statD.getName()).andReturn("statD");
    expectLastCall().atLeastOnce();
    expect(statD.read()).andReturn(4);

    control.replay();

    Stats.export(statB);
    Stats.export(statA);
    Stats.export(statC);
    Stats.export(statD);
    repo.runSampler(clock);
  }

  @Test
  public void testDelayedExport() throws InterruptedException {
    control.replay();

    AtomicLong earlyExport = Stats.exportLong("early");

    clock.setNowMillis(1000);

    for (int i = 0; i < 4; i++) {
      earlyExport.addAndGet(2);
      repo.runSampler(clock);
      clock.waitFor(1000);
    }

    expectTimestamps(1000, 2000, 3000, 4000);
    expectSeriesData("early", 2, 4, 6, 8);

    AtomicLong delayedExport = Stats.exportLong("delayed");
    delayedExport.set(100);
    earlyExport.addAndGet(2);
    repo.runSampler(clock);

    expectTimestamps(1000, 2000, 3000, 4000, 5000);
    expectSeriesData("early", 2, 4, 6, 8, 10);
    expectSeriesData("delayed", 0, 0, 0, 0, 100);
  }

  private void expectTimestamps(long... timestamps) {
    List<Number> expectedTimestamps = Lists.newArrayList();
    for (long timestamp : timestamps) expectedTimestamps.add(timestamp);
    assertThat(Lists.newArrayList(repo.getTimestamps()), is(expectedTimestamps));
  }

  private void expectSeriesData(String series, long... values) {
    List<Number> expectedValues = Lists.newArrayList();
    for (long value : values) expectedValues.add(value);
    assertThat(Lists.newArrayList(repo.get(series).getSamples()), is(expectedValues));
  }
}
