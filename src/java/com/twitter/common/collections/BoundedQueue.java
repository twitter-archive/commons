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
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A limited implementation of a bounded queue.  Values can be added and iterated over, and will
 * automatically expire when the queue exceeds capacity.
 *
 * @param <T> The type that this queue contains.
 *
 * @author William Farner
*/
public class BoundedQueue<T> implements Iterable<T> {
  private final LinkedBlockingDeque<T> values;

  /**
   * Creates a new bounded queue.
   *
   * @param limit Maximum number of items that can be in the queue at any time.
   */
  public BoundedQueue(int limit) {
    values = new LinkedBlockingDeque<T>(limit);
  }

  /**
   * Adds a value to head of the queue, evicting the oldest item if the queue is at capacity.
   *
   * @param value Value to add.
   */
  public synchronized void add(T value) {
    if (values.remainingCapacity() == 0) {
      values.removeFirst();
    }
    values.addLast(value);
  }

  /**
   * Removes all values from the queue.
   */
  public synchronized void clear() {
    values.clear();
  }

  /**
   * Returns the size of the queue.
   *
   * @return The current queue length.
   */
  public synchronized int size() {
    return values.size();
  }

  @Override
  public synchronized Iterator<T> iterator() {
    return values.iterator();
  }

  @Override
  public synchronized String toString() {
    return values.toString();
  }
}
