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

import junit.framework.TestCase;

public class BagTest extends TestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void testing() throws Exception {
    Bag<String> bag = new Bag<String>();
    assertEquals("Bag empty size:", 0, bag.size());

    bag.add("one");
    assertEquals("Bag adding element:", 1, bag.size());
    assertEquals("Bag element count:", 1, bag.getCount("one"));
    assertEquals("Bag total count:", 1, bag.getTotalCount());

    bag.add("one");
    assertEquals("Bag adding element:", 1, bag.size());
    assertEquals("Bag element count:", 2, bag.getCount("one"));
    assertEquals("Bag total count:", 2, bag.getTotalCount());

    // Add 10 two's...
    bag.add("two");
    bag.add("two");
    bag.add("two");
    bag.add("two");
    bag.add("two");
    bag.add("two");
    bag.add("two");
    bag.add("two");
    bag.add("two");
    bag.add("two");
    assertEquals("Bag adding element:", 2, bag.size());
    assertEquals("Bag element count:", 10, bag.getCount("two"));
    assertEquals("Bag total count:", 12, bag.getTotalCount());

    bag.removeIfCountLessThan(3);
    assertEquals("Bag adding element:", 1, bag.size());
    assertEquals("Bag total count:", 10, bag.getTotalCount());

    bag.add("one");
    assertEquals("Bag adding element:", 2, bag.size());
    assertEquals("Bag total count:", 11, bag.getTotalCount());
    bag.removeIfCountGreaterThan(2);
    assertEquals("Bag total count:", 1, bag.getTotalCount());

    bag.clear();
    assertEquals("Bag adding element:", 0, bag.size());
  }
}
