package com.twitter.common.metrics;

import com.google.common.base.Supplier;

import org.junit.Test;

import com.twitter.common.testing.EasyMockTest;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

/**
 * Test for Ratio.
 */
public class RatioTest extends EasyMockTest {

  private static final double EPS = 1e-8;

  @Test
  public void testDivByZero() {
    Supplier<Integer> numerator = createMock(new Clazz<Supplier<Integer>>() { });
    Supplier<Long> denominator = createMock(new Clazz<Supplier<Long>>() { });

    expect(numerator.get()).andReturn(100);
    expect(denominator.get()).andReturn(0L);

    control.replay();

    Ratio ratio = new Ratio("foo", numerator, denominator);
    assertEquals(Double.NaN, ratio.read(), EPS);
  }

  @Test
  public void testNaN() {
    Supplier<Double> numerator = createMock(new Clazz<Supplier<Double>>() { });
    Supplier<Double> denominator = createMock(new Clazz<Supplier<Double>>() { });

    expect(numerator.get()).andReturn(100d);
    expect(denominator.get()).andReturn(Double.NaN);
    expect(numerator.get()).andReturn(Double.NaN);
    expect(denominator.get()).andReturn(100d);

    control.replay();

    Ratio ratio = new Ratio("foo", numerator, denominator);
    assertEquals(Double.NaN, ratio.read(), EPS);
    assertEquals(Double.NaN, ratio.read(), EPS);
  }
}
