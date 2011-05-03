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

package com.twitter.common.collections;

import com.google.common.base.Function;
import com.google.common.base.Functions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author John Sirois
 */
public class PairTest {
  @Test
  public void testSlots() {
    assertEquals("jake", Pair.of("jake", null).getFirst());
    assertNull(Pair.of("jake", null).getSecond());
    assertNull(Pair.of(null, "jim").getFirst());
    assertEquals("jim", Pair.of(null, "jim").getSecond());
  }

  @Test
  public void testValueEquals() {
    assertEquals(new Pair<Integer, String>(1, "a"), Pair.of(1, "a"));
    assertEquals(new Pair<Integer, String>(null, "a"), Pair.<Integer, String>of(null, "a"));
    assertEquals(new Pair<Integer, String>(1, null), Pair.<Integer, String>of(1, null));
  }

  @Test
  public void testExtractors() {
    Function<Pair<Pair<String, Integer>, Character>, Pair<String, Integer>> first = Pair.first();
    Function<Pair<String, Integer>, Integer> second = Pair.second();
    assertEquals(Integer.valueOf(42),
        Functions.compose(second, first).apply(Pair.of(Pair.of("arthur", 42), 'z')));
  }
}
