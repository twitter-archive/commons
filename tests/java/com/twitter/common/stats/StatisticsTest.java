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

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.ImmutableList;

/**
 * Tests the functionality of the Statistics class.
 *
 * @author William Farner
 */
public class StatisticsTest extends TestCase {
  private static final double ERROR_THRESHOLD = 1e-10;

  private static final List<Integer> EMPTY_SET = ImmutableList.of();

  private static final List<Integer> TEST_SET_A = Arrays.asList(76117373, 76167137, 75870125, 75880508, 78099974,
                                       77810738, 75763975, 78042301, 76109165, 77816921,
                                       76115544, 76075750, 75391297, 75597249, 77793835,
                                       76001118, 77752542, 78413670, 60351776, 75607235,
                                       76057629, 80011920, 24067379, 75767484, 80052983,
                                       79278613, 75600277);

  private Statistics createAndLoad(List<Integer> values) {
    Statistics stats = new Statistics();
    for (long value : values) {
      stats.accumulate(value);
    }

    return stats;
  }

  private void checkWithinThreshold(double expected, double actual) {
    assertTrue(Math.abs(expected - actual) < ERROR_THRESHOLD);
  }

  public void testMin() {
    // min is undefined for an empty set, but it should not fail.
    Statistics stats = createAndLoad(EMPTY_SET);
    stats.min();

    stats = createAndLoad(TEST_SET_A);
    assertEquals(24067379, stats.min());
  }

  public void testMax() {
    // max is undefined for an empty set, but it should not fail.
    Statistics stats = createAndLoad(EMPTY_SET);
    stats.max();

    stats = createAndLoad(TEST_SET_A);
    assertEquals(80052983, stats.max());
  }

  public void testMean() {
    // mean is undefined for an empty set, but it should not fail.
    Statistics stats = createAndLoad(EMPTY_SET);
    stats.mean();

    stats = createAndLoad(TEST_SET_A);
    checkWithinThreshold(7.435609325925925E7, stats.mean());
  }

  public void testVariance() {
    Statistics stats = createAndLoad(EMPTY_SET);
    assertEquals(Double.NaN, stats.variance());

    stats = createAndLoad(TEST_SET_A);
    checkWithinThreshold(1.089077613763465E14, stats.variance());
  }

  public void testStandardDeviation() {
    Statistics stats = createAndLoad(EMPTY_SET);
    assertEquals(Double.NaN, stats.standardDeviation());

    stats = createAndLoad(TEST_SET_A);
    checkWithinThreshold(1.0435888145066835E7, stats.standardDeviation());
  }

  public void testPopulationSize() {
    Statistics stats = createAndLoad(EMPTY_SET);
    assertEquals(0L, stats.populationSize());

    stats = createAndLoad(TEST_SET_A);
    assertEquals(TEST_SET_A.size(), stats.populationSize());
  }

  public void testSum() {
    Statistics stats = createAndLoad(EMPTY_SET);
    assertEquals(0L, stats.sum());

    stats = createAndLoad(TEST_SET_A);
    long expectedSum = 0;
    for (long x: TEST_SET_A) {
      expectedSum += x;
    }
    assertEquals(expectedSum, stats.sum());
  }

}
