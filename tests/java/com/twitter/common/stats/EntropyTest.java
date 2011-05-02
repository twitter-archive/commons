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

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.List;

/**
 * Tests Entropy.
 *
 * @author Gilad Mishne
 */
public class EntropyTest extends TestCase {

  private void assertEqualsWithDeviation(double expected, double predicted, double deviation) {
    assertTrue(String.format("%2.4f not within %2.4f distance of %2.4f",
        predicted, deviation, expected),
        Math.abs(expected - predicted) <= deviation);
  }

  @Test
  public void test() throws Exception {
    List<Integer> numbers = Lists.newArrayList();
    double deviation = 0.01;

    assertEqualsWithDeviation(new Entropy<Integer>(numbers).entropy(), 0, deviation);

    numbers.add(1);
    assertEqualsWithDeviation(new Entropy<Integer>(numbers).entropy(), 0, deviation);

    numbers.add(2);
    assertEqualsWithDeviation(new Entropy<Integer>(numbers).entropy(), 1, deviation);

    numbers.addAll(Lists.newArrayList(1, 2));
    assertEqualsWithDeviation(new Entropy<Integer>(numbers).entropy(), 1, deviation);
    assertEqualsWithDeviation(new Entropy<Integer>(numbers).perplexity(), 2, deviation);

    numbers.addAll(Lists.newArrayList(2, 2, 3, 4));
    assertEqualsWithDeviation(new Entropy<Integer>(numbers).entropy(), 1.75, deviation);

    numbers.addAll(Lists.newArrayList(1, 1, 1, 1));
    assertEqualsWithDeviation(new Entropy<Integer>(numbers).entropy(), 1.625, deviation);
  }
}
