package com.twitter.common.metrics;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.testing.FakeClock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests metric scoping and sampling with different types of gauges.
 */
public class MetricsIT {

  private static final double EPS = 1e-8;

  private static final Amount<Long, Time> ONE_SECOND = Amount.of(1L, Time.SECONDS);

  private FakeClock clock;
  private Metrics root;

  @Before
  public void setUp() {
    clock = new FakeClock();
    root = new Metrics();
  }

  @Test
  public void testEmpty() {
    checkSamples(ImmutableMap.<String, Number>of());
  }

  @Test
  public void testDerivedVars() {
    AtomicLong longVar = root.registerLong("long");

    MetricRegistry barScope = root.scope("bar");
    Rate longRate = Rate.of("long", longVar, clock);
    barScope.register(longRate);
    Rate longRateRate = Rate.of(longRate, clock);
    root.register(longRateRate);

    checkSamples(ImmutableMap.<String, Number>of(
        "long", 0L, "bar." + longRate.getName(), 0d, longRateRate.getName(), 0d));

    clock.advance(ONE_SECOND);
    longVar.addAndGet(10);
    checkSamples(ImmutableMap.<String, Number>of(
        "long", 10L, "bar." + longRate.getName(), 10d, longRateRate.getName(), 10d));

    clock.advance(ONE_SECOND);
    longVar.addAndGet(2);
    checkSamples(ImmutableMap.<String, Number>of(
        "long", 12L, "bar." + longRate.getName(), 2d, longRateRate.getName(), -8d));
  }

  private void checkSamples(Map<String, Number> expected) {
    Map<String, Number> samples = Maps.newHashMap(root.sample());
    for (Map.Entry<String, Number> expectedSample : expected.entrySet()) {
      Number value = samples.remove(expectedSample.getKey());
      assertNotNull("Missing metric " + expectedSample.getKey(), value);
      assertEquals("Incorrect value for " + expectedSample.getKey(),
          expectedSample.getValue().doubleValue(), value.doubleValue(), EPS);
    }
    assertEquals("Unexpected metrics " + samples.keySet(), 0, samples.size());
  }
}
