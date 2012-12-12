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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

/**
 * Utility class for functions related to Multimaps in the java collections library.
 *
 * @author William Farner
 */
public final class Multimaps {

  private Multimaps() {
    // Utility.
  }

  /**
   * Prunes a multimap based on a predicate, returning the pruned values.  The input map will be
   * modified.
   *
   * @param map The multimap to prune.
   * @param filterRule The pruning rule.  When the predicate returns {@code false} for an entry, it
   *    will be pruned, otherwise it will be retained.
   * @param <K> The key type in the multimap.
   * @param <V> The value type in the multimap.
   * @return A new multimap, containing the pruned keys/values.
   */
  public static <K, V> Multimap<K, V> prune(Multimap<K, V> map,
      Predicate<? super Collection<V>> filterRule) {
    Preconditions.checkNotNull(map);
    Preconditions.checkNotNull(filterRule);
    Multimap<K, V> pruned = ArrayListMultimap.create();
    Iterator<Map.Entry<K, Collection<V>>> asMapItr = map.asMap().entrySet().iterator();
    while (asMapItr.hasNext()) {
      Map.Entry<K, Collection<V>> asMapEntry = asMapItr.next();
      if (!filterRule.apply(asMapEntry.getValue())) {
        pruned.putAll(asMapEntry.getKey(), asMapEntry.getValue());
        asMapItr.remove();
      }
    }

    return pruned;
  }

  private static final class AtLeastSize implements Predicate<Collection<?>> {
    private final int minSize;

    AtLeastSize(int minSize) {
      Preconditions.checkArgument(minSize >= 0);
      this.minSize = minSize;
    }

    @Override
    public boolean apply(Collection<?> c) {
      return c.size() >= minSize;
    }
  }

  /**
   * Convenience method to prune key/values pairs where the size of the value collection is below a
   * threshold.
   *
   * @param map The multimap to prune.
   * @param minSize The minimum size for retained value collections.
   * @param <K> The key type in the multimap.
   * @param <V> The value type in the multimap.
   * @return A new multimap, containing the pruned keys/values.
   * @throws IllegalArgumentException if minSize < 0
   */
  public static <K, V> Multimap<K, V> prune(Multimap<K, V> map, int minSize) {
    return prune(map, new AtLeastSize(minSize));
  }

  /**
   * Returns the set of keys associated with groups of a size greater than or equal to a given size.
   *
   * @param map The multimap to search.
   * @param minSize The minimum size to return associated keys for.
   * @param <K> The key type for the multimap.
   * @return The keys associated with groups of size greater than or equal to {@code minSize}.
   * @throws IllegalArgumentException if minSize < 0
   */
  public static <K> Set<K> getLargeGroups(Multimap<K, ?> map, int minSize) {
    return Sets.newHashSet(
        Maps.filterValues(map.asMap(), new AtLeastSize(minSize)).keySet());
  }

  /**
   * Returns the set of keys associated with the largest values in the multimap.
   *
   * @param map The multimap to search.
   * @param topValues Number of groupings to find the keys for.
   * @return The keys associated with the largest groups, of maximum size {@code topValues}.
   */
  public static <K> Set<K> getLargestGroups(Multimap<K, ?> map, int topValues) {
    Ordering<Multiset.Entry<K>> groupOrdering = new Ordering<Multiset.Entry<K>>() {
      @Override
      public int compare(Multiset.Entry<K> entry1, Multiset.Entry<K> entry2) {
        return entry1.getCount() - entry2.getCount();
        // overflow-safe, since sizes are nonnegative
      }
    };
    Set<K> topKeys = Sets.newHashSetWithExpectedSize(topValues);
    for (Multiset.Entry<K> entry
         : groupOrdering.greatestOf(map.keys().entrySet(), topValues)) {
      topKeys.add(entry.getElement());
    }
    return topKeys;
  }
}
