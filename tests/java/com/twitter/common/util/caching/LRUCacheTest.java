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

package com.twitter.common.util.caching;

import com.google.common.collect.Lists;
import com.twitter.common.base.Closure;
import com.twitter.common.collections.Pair;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * Tests the LRUCache class.
 *
 * @author William Farner
 */
public class LRUCacheTest {

  @Test
  public void testEvicts() {
    int cacheSize = 10;
    int inserts = 100;
    LRUCache<Integer, Integer> cache = LRUCache.<Integer, Integer>builder()
        .maxSize(cacheSize)
        .build();
    for (int i = 0; i < inserts; i++) {
      cache.put(i, i);
      assertThat(cache.size(), is(Math.min(i + 1, cacheSize)));
    }
  }

  @Test
  public void testEvictsLRU() {
    int cacheSize = 10;

    final List<Integer> evictedKeys = Lists.newLinkedList();

    Closure<Pair<Integer, Integer>> listener = new Closure<Pair<Integer, Integer>>() {
        @Override public void execute(Pair<Integer, Integer> evicted) {
          evictedKeys.add(evicted.getFirst());
        }
    };

    LRUCache<Integer, Integer> cache = LRUCache.<Integer, Integer>builder()
        .maxSize(cacheSize)
        .evictionListener(listener)
        .build();

    for (int i = 0; i < cacheSize; i++) {
      cache.put(i, i);
    }

    // Access all elements except '3'.
    for (int access : Arrays.asList(0, 7, 2, 8, 4, 6, 9, 1, 5)) {
      assertNotNull(cache.get(access));
    }

    assertThat(evictedKeys.isEmpty(), is(true));

    // This should trigger the eviction.
    cache.put(cacheSize + 1, cacheSize + 1);

    assertThat(evictedKeys, is(Arrays.asList(3)));
  }
}
