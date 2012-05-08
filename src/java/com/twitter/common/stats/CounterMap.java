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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A map from a key type to integers.  This simplifies the process of storing counters for multiple
 * values of the same type.
 */
public class CounterMap <K> implements Iterable<Map.Entry<K, Integer>>, Cloneable {
  private final Map<K, Integer> map = Maps.newHashMap();

  private static Logger log = Logger.getLogger(CounterMap.class.getName());

  /**
   * Increments the counter value associated with {@code key}, and returns the new value.
   *
   * @param key The key to increment
   * @return The incremented value.
   */
  public int incrementAndGet(K key) {
    return incrementAndGet(key, 1);
  }

  /**
   * Increments the value associated with {@code key} by {@code value}, returning the new value.
   *
   * @param key The key to increment
   * @return The incremented value.
   */
  public int incrementAndGet(K key, int count) {
    Integer value = map.get(key);
    if (value == null) {
      value = 0;
    }
    int newValue = count + value;
    map.put(key, newValue);
    return newValue;
  }

  /**
   * Gets the value associated with a key.
   *
   * @param key The key to look up.
   * @return The counter value stored for {@code key}, or 0 if no mapping exists.
   */
  public int get(K key) {
    if (!map.containsKey(key)) {
      return 0;
    }

    return map.get(key);
  }

  /**
   * Assigns a value to a key.
   *
   * @param key The key to assign a value to.
   * @param newValue The value to assign.
   */
  public void set(K key, int newValue) {
    Preconditions.checkNotNull(key);
    map.put(key, newValue);
  }

  /**
   * Resets the value for {@code key}.  This will remove the key from the counter.
   *
   * @param key The key to reset.
   */
  public void reset(K key) {
    map.remove(key);
  }

  /**
   * Gets the number of entries stored in the map.
   *
   * @return The size of the map.
   */
  public int size() {
    return map.size();
  }

  /**
   * Gets an iterator for the mapped values.
   *
   * @return Iterator for mapped values.
   */
  public Iterator<Map.Entry<K, Integer>> iterator() {
    return map.entrySet().iterator();
  }

  public Collection<Integer> values() {
    return map.values();
  }

  public Set<K> keySet() {
    return map.keySet();
  }

  public String toString() {
    StringBuilder strVal = new StringBuilder();
    for (Map.Entry<K, Integer> entry : this) {
      strVal.append(entry.getKey().toString()).append(": ").append(entry.getValue()).append('\n');
    }
    return strVal.toString();
  }

  public Map<K, Integer> toMap() {
    return map;
  }

  @Override
  public CounterMap<K> clone() {
    CounterMap<K> newInstance = new CounterMap<K>();
    newInstance.map.putAll(map);
    return newInstance;
  }
}
