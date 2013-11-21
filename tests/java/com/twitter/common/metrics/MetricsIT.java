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
    Counter longVar = root.createCounter("long");
    MetricRegistry barScope = root.scope("bar");
    Counter longVar2 = barScope.createCounter("long");
    checkSamples(ImmutableMap.<String, Number>of("long", 0L, "bar.long", 0L));

    longVar.add(10);
    checkSamples(ImmutableMap.<String, Number>of("long", 10L, "bar.long", 0L));

    longVar2.add(20);
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
