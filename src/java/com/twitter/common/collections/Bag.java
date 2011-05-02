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
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Abstract class that extends a Set<E> with the added
 * functionality it counts how many times an element has
 * been added to the Set<E> and can filter elements that
 * don't meet some frequency.
 *
 * @deprecated Please use com.google.common.collect.Multiset instead.
 *
 * @author Abdur Chowdhury
 *
 * @param <E>
 */
@Deprecated
public class Bag<E> extends TreeSet<E> {
  private static final long serialVersionUID = 1L;
  private Map<E, long[]> countedSet = null;
  private long totalFrequency;

  /**
   * Creates a new empty bag.
   */
  public Bag() {
    countedSet = new TreeMap<E, long[]>();
    totalFrequency = 0;
  }

  @Override
  public boolean add(E o) {
    long[] count = countedSet.get(o);
    if (count == null) {
      count = new long[1];
      count[0] = 1;
      countedSet.put(o, count);
    } else {
      count[0]++;
    }
    totalFrequency++;
    return super.add(o);
  }

  /**
   * Returns the frequency an element has been added to the Set<E>
   * @param o
   * @return
   */
  public long getCount(E o) {
    long[] count = countedSet.get(o);
    if (count == null) {
      return 0;
    } else {
      return count[0];
    }
  }

  /**
   * Returns the total objects added to the Set<E>
   *
   * @return
   */
  public long getTotalCount() {
    return totalFrequency;
  }

  @Override
  public Iterator<E> iterator() {
    return countedSet.keySet().iterator();
  }

  @Override
  public void clear() {
    super.clear();
    countedSet.clear();
    totalFrequency = 0;
  }

  /**
   * Return the number of elements stored in the Bag<E>.
   */
  @Override
  public int size() {
    return countedSet.size();
  }

  /**
   * Filters objects from the Set<E> that have been added
   * less than X times.
   *
   * @param freq
   * @return
   */
  public int removeIfCountLessThan(int freq) {
    E key = null;
    int removed = 0;
    long count;
    Iterator<E> it = countedSet.keySet().iterator();
    while (it.hasNext()) {
      key = it.next();
      count = countedSet.get(key)[0];
      if (count < freq) {
        it.remove();
        super.remove(key);
        removed++;
        totalFrequency -= count;
      }
    }
    return removed;
  }

  /**
   * Filters objects from the Set<E> that have been added
   * greater than X times.
   *
   * @param freq
   * @return
   */
  public int removeIfCountGreaterThan(int freq) {
    E key = null;
    int removed = 0;
    long count;
    Iterator<E> it = countedSet.keySet().iterator();
    while (it.hasNext()) {
      key = it.next();
      count = countedSet.get(key)[0];
      if (count > freq) {
        it.remove();
        super.remove(key);
        removed++;
        totalFrequency -= count;
      }
    }
    return removed;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    E key = null;
    long count;
    Iterator<E> it = countedSet.keySet().iterator();
    while (it.hasNext()) {
      key = it.next();
      count = countedSet.get(key)[0];
      sb.append("K:" + key + "\tV:" + count + "\n");
    }
    return sb.toString();
  }

}
