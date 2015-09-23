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

import com.twitter.common.base.Supplier;
import com.twitter.common.util.testing.FakeTicker;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.util.testing.FakeTicker;

import java.util.concurrent.atomic.AtomicLong;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * @author William Farner
 */
public class RateTest {

  private static final int ONE_SEC = 1000000000;
  private static final double EPSILON = 1E-6;

  private IMocksControl control;
  private FakeTicker ticker;

  private Stat<Integer> input;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    control = createControl();

    ticker = new FakeTicker();
    input = control.createMock(Stat.class);
  }

  @After
  public void verify() {
    Stats.flush();
    control.verify();
  }

  @Test
  public void testInputsRegistered() {
    expect(input.getName()).andReturn("test");
    expectLastCall().atLeastOnce();

    control.replay();

    Rate.of(input);
    assertNotNull(Stats.getVariable("test"));
  }

  @Test
  public void testNoHistory() throws Exception {
    expectCalls(0);

    control.replay();

    assertResults();
  }

  @Test
  public void testFlat() throws Exception {
    expectCalls(10, 10);

    control.replay();

    assertResults(0);
  }

  @Test
  public void testFixedRate() throws Exception {
    expectCalls(10, 20, 30, 40);

    control.replay();

    assertResults(10, 10, 10);
  }

  @Test
  public void testVariableRate() throws Exception {
    expectCalls(10, 20, 50, 150);

    control.replay();

    assertResults(10, 30, 100);
  }

  @Test
  public void testVariableRateAtomicLong() throws Exception {
    AtomicLong value = new AtomicLong();
    Rate<AtomicLong> rate = Rate.of("test", value).withTicker(ticker).build();

    ticker.waitNanos(ONE_SEC);
    value.set(10);
    assertEquals(0d, rate.sample(), EPSILON);

    ticker.waitNanos(ONE_SEC);
    value.set(20);
    assertEquals(10d, rate.sample(), EPSILON);

    ticker.waitNanos(ONE_SEC);
    value.set(50);
    assertEquals(30d, rate.sample(), EPSILON);

    ticker.waitNanos(ONE_SEC);
    value.set(100);
    assertEquals(50d, rate.sample(), EPSILON);

    control.replay();
  }

  @Test
  public void testNegativeRate() throws Exception {
    expectCalls(40, 30, 20, 10);

    control.replay();

    assertResults(-10, -10, -10);
  }

  @Test
  public void testZeroDelta() throws Exception {
    expectCalls(10, 10, 10);

    control.replay();

    assertResults(0, 0);
  }

  @Test
  public void testLongWindow() throws Exception {
    expectCalls(10, 10, 0, 10, 10);

    control.replay();

    assertResults(Rate.of(input).withWindowSize(3).withTicker(ticker).build(), 0, -5, 0, 0);
  }

  @Test
  public void testRateOfRate() throws Exception {
    expectCalls(10, 20, 30, 40, 50, 60);

    control.replay();

    Rate<Integer> rate = Rate.of(input).withTicker(ticker).build();
    Rate<Double> rateOfRate = Rate.of(rate).withTicker(ticker).build();

    assertThat(rate.sample(), is(0d));
    assertThat(rateOfRate.sample(), is(0d));
    ticker.waitNanos(ONE_SEC);
    assertThat(rate.sample(), is(10d));
    assertThat(rateOfRate.sample(), is(10d));
    ticker.waitNanos(ONE_SEC);
    assertThat(rate.sample(), is(10d));
    assertThat(rateOfRate.sample(), is(0d));
    ticker.waitNanos(ONE_SEC);
    assertThat(rate.sample(), is(10d));
    assertThat(rateOfRate.sample(), is(0d));
    ticker.waitNanos(ONE_SEC);
    assertThat(rate.sample(), is(10d));
    assertThat(rateOfRate.sample(), is(0d));
    ticker.waitNanos(ONE_SEC);
    assertThat(rate.sample(), is(10d));
    assertThat(rateOfRate.sample(), is(0d));
  }

  @Test
  public void testScaleFactor() throws Exception {
    expectCalls(10, 20, 30, 40);

    control.replay();

    assertResults(Rate.of(input).withTicker(ticker).withScaleFactor(10).build(), 100, 100, 100);
  }

  @Test
  public void testFractionalScaleFactor() throws Exception {
    expectCalls(10, 20, 30, 40);

    control.replay();

    assertResults(Rate.of(input).withTicker(ticker).withScaleFactor(0.1).build(), 1, 1, 1);
  }

  @Test
  public void testSupplier() throws Exception {
    Supplier<Long> supplier = new Supplier<Long>() {
      long value = 0;
      @Override public Long get() {
        value += 10;
        return value;
      }
    };

    control.replay();
    assertResults(Rate.of("test", supplier).withTicker(ticker).build(), 10, 10, 10);
  }

  private void expectCalls(int... samples) {
    expect(input.getName()).andReturn("test");
    expectLastCall().atLeastOnce();
    for (int sample : samples) {
      expect(input.read()).andReturn(sample);
    }
  }

  private void assertResults(double... results) throws Exception {
    assertResults(Rate.of(input).withTicker(ticker).build(), results);
  }

  private void assertResults(Rate rate, double... results) throws Exception {
    // First result is always zero.
    assertEquals(0d, rate.sample().doubleValue(), EPSILON);
    ticker.waitNanos(ONE_SEC);

    for (double result : results) {
      assertEquals(result, rate.sample().doubleValue(), EPSILON);
      ticker.waitNanos(ONE_SEC);
    }
  }
}
