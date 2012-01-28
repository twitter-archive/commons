package com.twitter.common.metrics;

import com.google.common.base.Supplier;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.EasyMockTest;
import com.twitter.common.util.testing.FakeClock;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

/**
 */
public class RateTest extends EasyMockTest {

  private static final double EPS = 1e-8;

  private static final Amount<Long, Time> TEN_SECONDS = Amount.of(10L, Time.SECONDS);
  private static final Amount<Long, Time> THIRTY_SECONDS = Amount.of(30L, Time.SECONDS);

  private Supplier<Number> valueSupplier;
  private FakeClock clock;

  @Before
  public void setUp() {
    valueSupplier = createMock(new Clazz<Supplier<Number>>() { });
    clock = new FakeClock();
  }

  @Test
  public void testEmpty() {
    // Any number will do - just showing that it doesn't impact the rate.
    expect(valueSupplier.get()).andReturn(100000);

    control.replay();

    Rate rate = makeRate("foo", THIRTY_SECONDS);
    assertEquals(rate.read(), 0d, EPS);
  }

  @Test
  public void testWindowing() {
    expect(valueSupplier.get()).andReturn(100);
    expect(valueSupplier.get()).andReturn(0);
    expect(valueSupplier.get()).andReturn(50);
    expect(valueSupplier.get()).andReturn(100);
    expect(valueSupplier.get()).andReturn(150);
    expect(valueSupplier.get()).andReturn(100);
    expect(valueSupplier.get()).andReturn(50);

    control.replay();

    Rate rate = makeRate("foo", THIRTY_SECONDS);
    assertEquals(rate.read(), 0d, EPS);

    clock.advance(TEN_SECONDS);
    assertEquals(-100d / 10, rate.read(), EPS);

    clock.advance(TEN_SECONDS);
    assertEquals(-50d / 20, rate.read(), EPS);

    clock.advance(TEN_SECONDS);
    assertEquals(0d, rate.read(), EPS);

    clock.advance(TEN_SECONDS);
    assertEquals(150d / 30, rate.read(), EPS);

    clock.advance(TEN_SECONDS);
    assertEquals(50d / 30, rate.read(), EPS);

    clock.advance(TEN_SECONDS);
    assertEquals(-50d / 30, rate.read(), EPS);
  }

  private Rate makeRate(String name, Amount<Long, Time> window) {
    return new Rate(name, valueSupplier, window, clock);
  }
}
