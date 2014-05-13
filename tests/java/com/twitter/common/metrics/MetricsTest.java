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

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests metric registry scoping.
 */
public class MetricsTest {

  private static final double EPS = 1e-8;

  private Metrics metrics;

  @Before
  public void setUp() {
    metrics = Metrics.createDetached();
  }

  @Test
  public void testEmpty() {
    checkSamples(ImmutableMap.<String, Number>of());
  }

  @Test
  public void testScoping() {
    Counter foo = metrics.createCounter("foo");
    foo.add(10);

    checkSamples(ImmutableMap.<String, Number>of("foo", 10L));

    MetricRegistry barScope = metrics.scope("bar");
    Counter barFoo = barScope.createCounter("foo");
    barFoo.add(2);

    checkSamples(ImmutableMap.<String, Number>of("foo", 10L, "bar.foo", 2L));

    MetricRegistry bazScope = barScope.scope("baz");
    Counter bazFoo = bazScope.createCounter("foo");
    bazFoo.add(3);

    checkSamples(ImmutableMap.<String, Number>of("foo", 10L, "bar.foo", 2L, "bar.baz.foo", 3L));
  }

  @Test
  public void testDetachedRoot() {
    String name = "foo";
    Long value = 10L;
    Metrics detached = Metrics.createDetached();
    Counter foo = detached.createCounter(name);
    foo.add(value);

    Long readValue = (Long) detached.sample().get(name);
    assertEquals(readValue, value);

    foo.increment();

    Long readValue2 = (Long) detached.sample().get(name);
    value += 1;
    assertEquals(readValue2, value);

    assertNull(Metrics.root().sample().get(name));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testOverwrite() {
    Counter foo = metrics.createCounter("foo");
    Counter foo2 = metrics.createCounter("foo");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testOverwrite2() {
    try {
      Counter foo = metrics.createCounter("foo");
      // nb. we check for collisions on base-name of the histogram but perhaps we want
      // to check the expanded quantile/etc names in the future.
      HistogramInterface foo2 = metrics.createHistogram("foo");
    } catch (IllegalArgumentException expected) {
      Gauge foo = new AbstractGauge("foo") {
        @Override
        public Number read() {
          return 12;
        }
      };
      metrics.register(foo);
      Counter foo2 = metrics.createCounter("foo");
    }
  }

  @Test
  public void testUnregisterByRef() {
    // Counter
    Counter counter = metrics.createCounter("counter");
    assertTrue("Metrics contains counter", metrics.sample().containsKey(counter.getName()));
    metrics.unregister(counter);
    assertFalse("Metrics doesn't contains counter",
        metrics.sample().containsKey(counter.getName()));

    // Gauge
    final long x = 1L;
    Gauge<Number> gauge = new AbstractGauge<Number>("gauge") {
      @Override public Number read() { return x; }
    };
    metrics.register(gauge);
    assertTrue("Metrics contains gauge", metrics.sample().containsKey(gauge.getName()));
    metrics.unregister(gauge);
    assertFalse("Metrics doesn't contains gauge", metrics.sample().containsKey(gauge.getName()));

    // Histogram
    HistogramInterface histo = metrics.createHistogram("histo");
    assertTrue("Metrics contains histo", metrics.sample().containsKey(
        histo.getName() + ".count"));
    metrics.unregister(histo);
    assertFalse("Metrics doesn't contains histo", metrics.sample().containsKey(
        histo.getName() + ".count"));
  }

  @Test
  public void testUnregisterByName() {
    // Counter
    Counter counter = metrics.createCounter("counter");
    assertTrue("Metrics contains counter", metrics.sample().containsKey(counter.getName()));
    metrics.unregister("counter");
    assertFalse("Metrics doesn't contains counter",
                   metrics.sample().containsKey(counter.getName()));

    // Gauge
    final long x = 1L;
    Gauge<Number> gauge = new AbstractGauge<Number>("gauge") {
      @Override public Number read() { return x; }
    };
    metrics.register(gauge);
    assertTrue("Metrics contains gauge", metrics.sample().containsKey(gauge.getName()));
    metrics.unregister(gauge.getName());
    assertFalse("Metrics doesn't contains gauge", metrics.sample().containsKey(gauge.getName()));

    // Histogram
    HistogramInterface histo = metrics.createHistogram("histo");
    assertTrue("Metrics contains histo", metrics.sample().containsKey(
        histo.getName() + ".count"));
    metrics.unregister(histo.getName());
    assertFalse("Metrics doesn't contains histo", metrics.sample().containsKey(
        histo.getName() + ".count"));
  }

  @Test
  public void testUnregistrationOnScopedRegistry() {
    String scope = "scope";
    MetricRegistry registry = metrics.scope(scope);

    // Counter
    Counter counter = registry.createCounter("counter");
    assertTrue("Metrics contains counter", metrics.sample().containsKey(counter.getName()));
    registry.unregister(counter);
    assertFalse("Metrics doesn't contains counter",
        metrics.sample().containsKey(counter.getName()));

    // Gauge
    final long x = 1L;
    Gauge<Number> gauge = registry.registerGauge(new AbstractGauge<Number>("gauge") {
      @Override public Number read() { return x; }
    });
    assertTrue("Metrics contains gauge", metrics.sample().containsKey(gauge.getName()));
    registry.unregister(gauge);
    assertFalse("Metrics doesn't contains gauge", metrics.sample().containsKey(gauge.getName()));

    // Histogram
    HistogramInterface histo = registry.createHistogram("histo");
    assertTrue("Metrics contains histo", metrics.sample().containsKey(
        histo.getName() + ".count"));
    registry.unregister(histo);
    assertFalse("Metrics doesn't contains histo", metrics.sample().containsKey(
        histo.getName() + ".count"));

    Counter counterZ = registry.createCounter("counter_zzz");
    assertTrue("Metrics contains counter", metrics.sample().containsKey(counterZ.getName()));
    registry.unregister("counter_zzz");
    assertFalse("Metrics doesn't contains counter",
        metrics.sample().containsKey(counterZ.getName()));
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
