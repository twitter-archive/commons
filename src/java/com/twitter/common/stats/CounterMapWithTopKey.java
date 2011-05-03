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

import java.util.Map;

/**
 * Same as CounterMap<K>, but also keeps track of the item with the highest count.
 */
public class CounterMapWithTopKey<K> extends CounterMap<K> {

  private K mostCommonKey = null;

  /**
   * Updates the most common key, if needed.
   *
   * @param key The key to check.
   * @param count The count for the key.
   * @return The count.
   */
  private int updateMostCommon(K key, int count) {
    if (count > get(mostCommonKey)) {
      mostCommonKey = key;
    }
    return count;
  }

  /**
   * Increments the counter value associated with {@code key}, and returns the new value.
   *
   * @param key The key to increment
   * @return The incremented value.
   */
  @Override
  public int incrementAndGet(K key) {
    return updateMostCommon(key, super.incrementAndGet(key));
  }

  /**
   * Assigns a value to a key.
   *
   * @param key The key to assign a value to.
   * @param newValue The value to assign.
   */
  @Override
  public void set(K key, int newValue) {
    super.set(key, updateMostCommon(key, newValue));
  }

  /**
   * Resets the value for {@code key}.  This will simply set the stored value to 0.
   * The most common key is updated by scanning the entire map.
   *
   * @param key The key to reset.
   */
  @Override
  public void reset(K key) {
    super.reset(key);
    for (Map.Entry<K, Integer> entry : this) {
      updateMostCommon(entry.getKey(), entry.getValue());
    }
  }

  /**
   *
   * @return The key with the highest count in the map. If multiple keys have this count, return
   * an arbitrary one.
   */
  public K getMostCommonKey() {
    return mostCommonKey;
  }

  @Override
  public String toString() {
    return new StringBuilder(super.toString()).append(String.format("Most common key: %s\n",
        mostCommonKey.toString())).toString();
  }
}
