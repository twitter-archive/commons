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
    long value  = 10L;
    Metrics detached = Metrics.createDetached();
    Counter foo = detached.createCounter(name);
    foo.add(value);

    Number readValue = detached.sample().get(name);
    assertEquals(readValue, value);

    foo.increment();

    Number readValue2 = detached.sample().get(name);
    assertEquals(readValue2, value + 1);

    assertNull(Metrics.root().sample().get(name));
  }

  @Test
  public void testOverwrite() {
    Counter foo = metrics.createCounter("foo");
    foo.add(10);

    Counter foo2 = metrics.createCounter("foo");
    foo2.add(100);

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
