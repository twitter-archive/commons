// =================================================================================================
// Copyright 2013 Twitter, Inc.
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

import java.lang.reflect.Array;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

/**
 * Windowed is an abstraction that let you span a class across a sliding window.
 * It creates a ring buffer of T and reuse the buffer after clearing it or use a new one (via
 * the {@code clearer} function).
 *
 * <pre>
 *          tenured instances
 *  ++++++++++++++++++++++++++++++++++
 * [----A-----][-----B----][-----C----][-----D----]
 *                                      ++++++++++
 *                                    current instance
 * </pre>
 *
 * The schema above represents the valid instances over time
 * (A,B,C) are the tenured ones
 * D is the current instance
 */
public abstract class Windowed<T> {
  private Class<T> clazz;
  protected final T[] buffers;
  private final long sliceDuration;
  private final Clock clock;
  private long index = -1L;
  private Function<T, T> clearer;

  /**
   * @param clazz the type of the underlying element T
   * @param window the length of the window
   * @param slices the number of slices (the window will be divided into {@code slices} slices)
   * @param sliceProvider the supplier of element
   * @param clearer the function that clear (or re-create) an element
   * @param clock  the clock used for to select the appropriate histogram
   */
  public Windowed(Class<T> clazz, Amount<Long, Time> window, int slices,
      Supplier<T> sliceProvider, Function<T, T> clearer, Clock clock) {
    Preconditions.checkNotNull(window);
    // Ensure that we have at least 1ms per slice
    Preconditions.checkArgument(window.as(Time.MILLISECONDS) > (slices + 1));
    Preconditions.checkArgument(window.as(Time.MILLISECONDS) > (slices + 1));
    Preconditions.checkArgument(0 < slices);
    Preconditions.checkNotNull(sliceProvider);
    Preconditions.checkNotNull(clock);

    this.clazz = clazz;
    this.sliceDuration = window.as(Time.MILLISECONDS) / slices;
    @SuppressWarnings("unchecked") // safe because we have the clazz proof of type H
    T[] bufs = (T[]) Array.newInstance(clazz, slices + 1);
    for (int i = 0; i < bufs.length; i++) {
      bufs[i] = sliceProvider.get();
    }
    this.buffers = bufs;
    this.clearer = clearer;
    this.clock = clock;
  }

  /**
   * Return the index of the latest Histogram.
   * You have to modulo it with buffer.length before accessing the array with this number.
   */
  protected int getCurrentIndex() {
    long now = clock.nowMillis();
    return (int) (now / sliceDuration);
  }

  /**
   * Check for expired elements and return the current one.
   */
  protected T getCurrent() {
    sync(getCurrentIndex());
    return buffers[(int) (index % buffers.length)];
  }

  /**
   * Check for expired elements and return all the tenured (old) ones.
   */
  protected T[] getTenured() {
    long currentIndex = getCurrentIndex();
    sync(currentIndex);
    @SuppressWarnings("unchecked") // safe because we have the clazz proof of type T
    T[] tmp = (T[]) Array.newInstance(clazz, buffers.length - 1);
    for (int i = 0; i < tmp.length; i++) {
      int idx = (int) ((currentIndex + 1 + i) % buffers.length);
      tmp[i] = buffers[idx];
    }
    return tmp;
  }

  /**
   * Clear all the elements.
   */
  public void clear() {
    for (int i = 0; i <= buffers.length; i++) {
      buffers[i] = clearer.apply(buffers[i]);
    }
  }

  /**
   * Synchronize elements with a point in time.
   * i.e. Check for expired ones and clear them, and update the index variable.
   */
  protected void sync(long currentIndex) {
    if (index < currentIndex) {
      long from = Math.max(index + 1, currentIndex - buffers.length + 1);
      for (long i = from; i <= currentIndex; i++) {
        int idx = (int) (i % buffers.length);
        buffers[idx] = clearer.apply(buffers[idx]);
      }
      index = currentIndex;
    }
  }
}
