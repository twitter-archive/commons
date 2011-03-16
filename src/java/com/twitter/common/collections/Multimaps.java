// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.collections;

import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
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
      Predicate<Collection<V>> filterRule) {
    Preconditions.checkNotNull(map);
    Preconditions.checkNotNull(filterRule);
    Set<K> prunedKeys = Sets.newHashSet();
    for (K key : map.keySet()) {
      if (!filterRule.apply(map.get(key))) {
        prunedKeys.add(key);
      }
    }

    Multimap<K, V> pruned = ArrayListMultimap.create();
    for (K key : prunedKeys) {
      pruned.putAll(key, map.removeAll(key));
    }

    return pruned;
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
   */
  public static <K, V> Multimap<K, V> prune(Multimap<K, V> map, final int minSize) {
    return prune(map, new Predicate<Collection<V>>() {
      @Override public boolean apply(Collection<V> input) {
        return input.size() >= minSize;
      }
    });
  }

  /**
   * Returns the set of keys associated with groups of a size greater than or equal to a given size.
   *
   * @param map The multimap to search.
   * @param minSize The minimum size to return associated keys for.
   * @param <K> The key type for the multimap.
   * @return The keys associated with groups of size greater than or equal to {@code minSize}.
   */
  public static <K> Set<K> getLargeGroups(Multimap<K, ?> map, int minSize) {
    Set<K> largeKeys = Sets.newHashSet();
    for (K key : map.keySet()) {
      if (map.get(key).size() >= minSize) {
        largeKeys.add(key);
      }
    }

    return largeKeys;
  }

  /**
   * Returns the set of keys associated with the largest values in the multimap.
   *
   * @param map The multimap to search.
   * @param topValues Number of groupings to find the keys for.
   * @return The keys associated with the largest groups, of maximum size {@code topValues}.
   */
  public static <K> Set<K> getLargestGroups(Multimap<K, ?> map, int topValues) {
    /**
     * A grouping of values in the multimap.
     */
    class Grouping implements Comparable<Grouping> {
      private K key;
      private int size;

      public Grouping(K key, int size) {
        this.key = key;
        this.size = size;
      }

      @Override
      public int hashCode() {
        return size;
      }
      @Override
      public int compareTo(Grouping grouping) {
        return size - grouping.size;
      }
      @Override
      public boolean equals(Object o) {
        if (!(o instanceof Grouping)) {
          return false;
        }
        Grouping other = (Grouping) o;
        return key.equals(other.key);
      }
    }

    SortedSet<Grouping> topGroups = Sets.newTreeSet();
    for (K key : map.keySet()) {
      topGroups.add(new Grouping(key, map.get(key).size()));

      // Remove the smallest value.
      if (topGroups.size() > topValues) {
        topGroups.remove(topGroups.first());
      }
    }

    Set<K> topKeys = Sets.newHashSet();
    for (Grouping group : topGroups) {
      topKeys.add(group.key);
    }

    return topKeys;
  }
}
