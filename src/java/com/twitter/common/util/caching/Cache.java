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

/**
 * Definition of basic caching functionality.  Cache keys and values are expected to always be
 * valid, non-null values.
 *
 * @author William Farner
 */
public interface Cache<K, V> {

  /**
   * Fetches a value from the cache.
   *
   * @param key The key for the value to fetch, must not be {@code null}.
   * @return The cached value corresponding with {@code key}, or {@code null} if no entry exists.
   */
  public V get(K key);

  /**
   * Stores a key-value pair in the cache.
   *
   * @param key The key to store, must not be {@code null}.
   * @param value The value to store, must not be {@code null}.
   */
  public void put(K key, V value);

  /**
   * Deletes an entry from the cache.
   *
   * @param key Key for the value to delete, must not be {@code null}.
   */
  public void delete(K key);
}
