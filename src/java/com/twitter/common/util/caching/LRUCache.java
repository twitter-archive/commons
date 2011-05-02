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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.twitter.common.base.Closure;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.collections.Pair;
import com.twitter.common.stats.Stats;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A cache with a fixed maximum size, evicting items that were used least-recently.
 * WARNING: This is not thread-safe.  If you wish to get a thread-safe version of a constructed
 * LRUCache, you must wrap it with {@link Collections#synchronizedMap(java.util.Map)}.
 *
 * @author William Farner
 */
public class LRUCache<K, V> implements Cache<K, V> {

  private Map<K, V> map;

  private final AtomicLong accesses;
  private final AtomicLong misses;

  /**
   * Creates a new bounded cache with the given load factor.
   *
   * @param name Unique name for this cache.
   * @param maxCapacity Maximum capacity for the cache, after which items will be evicted.
   * @param loadFactor Load factor for the cache.
   * @param makeSynchronized Whether the underlying map should be synchronized.
   * @param evictionListener Listener to be notified when an element is evicted, or {@code null} if
   *    eviction notifications are not needed.
   */
  private LRUCache(final String name, final int maxCapacity, float loadFactor,
      boolean makeSynchronized, final Closure<Pair<K, V>> evictionListener) {
    map = new LinkedHashMap<K, V>(maxCapacity, loadFactor, true /* Access order. */) {
      @Override public boolean removeEldestEntry(Map.Entry<K, V> entry) {
        boolean evict = size() > maxCapacity;
        if (evict && evictionListener != null) {
          evictionListener.execute(Pair.of(entry.getKey(), entry.getValue()));
        }
        return evict;
      }
    };

    if (makeSynchronized) {
      map = Collections.synchronizedMap(map);
    }

    accesses = Stats.exportLong(name + "_lru_cache_accesses");
    misses = Stats.exportLong(name + "_lru_cache_misses");
  }

  public static <K, V> Builder<K, V> builder() {
    return new Builder<K, V>();
  }

  public static class Builder<K, V> {
    private String name = null;

    private int maxSize = 1000;

    // Sadly, LinkedHashMap doesn't expose this, so the default is pulled from the javadoc.
    private float loadFactor = 0.75F;

    private boolean makeSynchronized = true;

    private Closure<Pair<K, V>> evictionListener = null;

    public Builder<K, V> name(String name) {
      this.name = MorePreconditions.checkNotBlank(name);
      return this;
    }

    public Builder<K, V> maxSize(int maxSize) {
      Preconditions.checkArgument(maxSize > 0);
      this.maxSize = maxSize;
      return this;
    }

    public Builder<K, V> loadFactor(float loadFactor) {
      this.loadFactor = loadFactor;
      return this;
    }

    public Builder<K, V> makeSynchronized(boolean makeSynchronized) {
      this.makeSynchronized = makeSynchronized;
      return this;
    }

    public Builder<K, V> evictionListener(Closure<Pair<K, V>> evictionListener) {
      this.evictionListener = evictionListener;
      return this;
    }

    public LRUCache<K, V> build() {
      return new LRUCache<K, V>(name, maxSize, loadFactor, makeSynchronized, evictionListener);
    }
  }

  @Override
  public V get(K key) {
    accesses.incrementAndGet();
    V value = map.get(key);
    if (value == null) {
      misses.incrementAndGet();
    }
    return value;
  }

  @Override
  public void put(K key, V value) {
    map.put(key, value);
  }

  @Override
  public void delete(K key) {
    map.remove(key);
  }

  public int size() {
    return map.size();
  }

  @Override
  public String toString() {
    return String.format("size: %d, accesses: %s, misses: %s",
        map.size(),
        accesses,
        misses);
  }

  public Collection<V> copyValues() {
    synchronized(map) {
      return ImmutableList.copyOf(map.values());
    }
  }

  public long getAccesses() {
    return accesses.longValue();
  }

  public long getMisses() {
    return misses.longValue();
  }

  public double getHitRate() {
    double numAccesses = accesses.longValue();
    return numAccesses == 0 ? 0 : (numAccesses - misses.longValue()) / numAccesses;
  }
}
