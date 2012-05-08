package com.twitter.common.metrics;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * Tests metric registry scoping.
 */
public class MetricsTest {

  private static final double EPS = 1e-8;

  private Metrics metrics;

  @Before
  public void setUp() {
    metrics = new Metrics();
  }

  @Test
  public void testEmpty() {
    checkSamples(ImmutableMap.<String, Number>of());
  }

  @Test
  public void testScoping() {
    AtomicLong foo = metrics.registerLong("foo");
    foo.getAndSet(10);

    checkSamples(ImmutableMap.<String, Number>of("foo", 10L));

    MetricRegistry barScope = metrics.scope("bar");
    AtomicLong barFoo = barScope.registerLong("foo");
    barFoo.getAndSet(2);

    checkSamples(ImmutableMap.<String, Number>of("foo", 10L, "bar.foo", 2L));

    MetricRegistry bazScope = barScope.scope("baz");
    AtomicLong bazFoo = bazScope.registerLong("foo");
    bazFoo.getAndSet(3);

    checkSamples(ImmutableMap.<String, Number>of("foo", 10L, "bar.foo", 2L, "bar.baz.foo", 3L));
  }

  @Test
  public void testDetachedRoot() {
    String name = "foo";
    long value  = 10L;
    Metrics detached = Metrics.createDetached();
    AtomicLong foo = detached.registerLong(name);
    foo.getAndSet(value);

    Number readValue = detached.sample().get(name);
    assertEquals(readValue, value);

    foo.incrementAndGet();

    Number readValue2 = detached.sample().get(name);
    assertEquals(readValue2, value + 1);

    assertNull(Metrics.root().sample().get(name));
  }

  @Test
  public void testOverwrite() {
    AtomicLong foo = metrics.registerLong("foo");
    foo.getAndSet(10);

    AtomicLong foo2 = metrics.registerLong("foo");
    foo2.getAndSet(100);

    checkSamples(ImmutableMap.<String, Number>of("foo", 100L));
  }

  private void checkSamples(Map<String, Number> expected) {
    Map<String, Number> samples = Maps.newHashMap(metrics.sample());
    for (Map.Entry<String, Number> expectedSample : expected.entrySet()) {
      Number value = samples.remove(expectedSample.getKey());
      assertNotNull(value);
      assertEquals(expectedSample.getValue().doubleValue(), value.doubleValue(), EPS);
    }

    assertThat(metrics.sample(), is(expected));
  }
}
