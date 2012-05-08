package com.twitter.common.metrics;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests metric scoping and sampling with different types of gauges.
 */
public class MetricsIT {

  private static final double EPS = 1e-8;

  private static final Amount<Long, Time> ONE_SECOND = Amount.of(1L, Time.SECONDS);

  private Metrics root;

  @Before
  public void setUp() {
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
    AtomicLong longVar2 = barScope.registerLong("long");
    checkSamples(ImmutableMap.<String, Number>of("long", 0L, "bar.long", 0L));

    longVar.addAndGet(10);
    checkSamples(ImmutableMap.<String, Number>of("long", 10L, "bar.long", 0L));

    longVar2.addAndGet(20);
    checkSamples(ImmutableMap.<String, Number>of("long", 10L, "bar.long", 20L));
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
