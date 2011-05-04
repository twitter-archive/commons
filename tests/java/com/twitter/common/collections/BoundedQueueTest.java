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

import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class BoundedQueueTest {

  private static final int SIZE = 4;

  private BoundedQueue<Integer> queue;

  @Before
  public void setUp() {
    queue = new BoundedQueue<Integer>(SIZE);
  }

  @Test
  public void testEmpty() {
    assertThat(queue.iterator().hasNext(), is(false));
  }

  @Test
  public void testFIFO() {
    queue.add(0);
    queue.add(1);
    queue.add(2);
    queue.add(3);
    queue.add(4);

    Iterator<Integer> it = queue.iterator();
    assertThat(it.next(), is(1));
    assertThat(it.next(), is(2));
    assertThat(it.next(), is(3));
    assertThat(it.next(), is(4));
  }
}
