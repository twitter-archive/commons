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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

/**
 * Histogram windowed over time.
 * <p>
 * This histogram is composed of a series of ({@code slices} + 1) histograms representing a window
 * of {@code range} duration. We only update the latest one, and we query the oldest ones (i.e. all
 * histograms except the head).
 * </p>
 * <pre>
 *      range
 * <------------->
 * [AAA][BBB][CCC][DDD]   here slices = 3
 * --------------------->
 *                t1  t2
 *
 *  For t in [t1,t2) we:
 *  insert elements in DDD
 *  query quantile over [AAA][BBB][CCC]
 * </pre>
 * <p>
 * When {@code t} is in {@code [t1, t2)} we insert value into the latest histogram (DDD here),
 * when we query the histogram, we 'merge' all other histograms (all except the latest) and query
 * it. when {@code t > t2} the oldest histogram become the newest (like in a ring buffer) and
 * so on ...
 * </p>
 * <p>
 * Note: We use MergedHistogram class to represent a merged histogram without actually
 * merging the underlying histograms.
 * </p>
 */
public class WindowedHistogram<H extends Histogram> extends Windowed<H> implements Histogram {

  private long mergedHistIndex = -1L;
  private Function<H[], Histogram> merger;
  private Histogram mergedHistogram = null;

  /**
   * Create a WindowedHistogram of {@code slices + 1} elements over a time {@code window}.
   * This code is independent from the implementation of Histogram, you just need to provide
   * a {@code Supplier<H>} to create the histograms and a {@code Function<H[], Histogram>} to
   * merge them.
   *
   * @param clazz the type of the underlying Histogram H
   * @param window the length of the window
   * @param slices the number of slices (the window will be divided into {@code slices} slices)
   * @param sliceProvider the supplier of histogram
   * @param merger the function that merge an array of histogram H[] into a single Histogram
   * @param clock the clock used for to select the appropriate histogram
   */
  public WindowedHistogram(Class<H> clazz, Amount<Long, Time> window, int slices,
        Supplier<H> sliceProvider, Function<H[], Histogram> merger, Clock clock) {
    super(clazz, window, slices, sliceProvider, new Function<H, H>() {
      @Override
      public H apply(H h) { h.clear(); return h; }
    }, clock);
    Preconditions.checkNotNull(merger);

    this.merger = merger;
  }

  @Override
  public synchronized void add(long x) {
    getCurrent().add(x);
  }

  @Override
  public synchronized void clear() {
    for (Histogram h: buffers) {
      h.clear();
    }
  }

  @Override
  public synchronized long getQuantile(double quantile) {
    long currentIndex = getCurrentIndex();
    if (mergedHistIndex < currentIndex) {
      H[] tmp = getTenured();
      mergedHistogram = merger.apply(tmp);
      mergedHistIndex = currentIndex;
    }
    return mergedHistogram.getQuantile(quantile);
  }

  @Override
  public synchronized long[] getQuantiles(double[] quantiles) {
    return Histograms.extractQuantiles(this, quantiles);
  }
}
