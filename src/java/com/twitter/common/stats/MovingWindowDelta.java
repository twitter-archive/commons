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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

import java.util.concurrent.LinkedBlockingDeque;

import com.twitter.common.base.MorePreconditions;


/**
 * Delta over the most recent k sample periods.
 *
 * If you use this class with a counter, you can get the cumulation of counts in a sliding window.
 *
 * One sample period is the time in between doSample() calls.
 *
 * @author Feng Zhuge
 */
public class MovingWindowDelta<T extends Number> extends SampledStat<Long> {
  private static final int DEFAULT_WINDOW_SIZE = 60;
  private final LinkedBlockingDeque<Long> deltaSeries;
  private final Supplier<T> inputAccessor;
  long sumDelta = 0l;
  long lastInput = 0l;

  private MovingWindowDelta(String name, Supplier<T> inputAccessor, int windowSize) {
    super(name, 0l);

    Preconditions.checkArgument(windowSize >= 1);
    Preconditions.checkNotNull(inputAccessor);
    MorePreconditions.checkNotBlank(name);

    deltaSeries = new LinkedBlockingDeque<Long>(windowSize);
    this.inputAccessor = inputAccessor;

    Stats.export(this);
  }

  /**
   * Create a new MovingWindowDelta instance.
   *
   * @param name The name of the value to be tracked.
   * @param inputAccessor The accessor of the value.
   * @param windowSize How many sample periods shall we use to calculate delta.
   * @param <T> The type of the value.
   * @return The created MovingWindowSum instance.
   */
  public static <T extends Number> MovingWindowDelta<T> of(
      String name, Supplier<T> inputAccessor, int windowSize) {
    return new MovingWindowDelta<T>(name, inputAccessor, windowSize);
  }

  /**
   * Create a new MovingWindowDelta instance using the default window size (currently 60).
   *
   * @param name The name of the value to be tracked.
   * @param inputAccessor The accessor of the value.
   * @param <T> The type of the value.
   * @return The created MovingWindowSum instance.
   */
  public static <T extends Number> MovingWindowDelta<T> of(String name, Supplier<T> inputAccessor) {
    return of(name, inputAccessor, DEFAULT_WINDOW_SIZE);
  }

  @Override
  public Long doSample() {
    long lastDelta = 0l;
    if (deltaSeries.remainingCapacity() == 0) {
      lastDelta = deltaSeries.removeFirst();
    }

    long newInput = inputAccessor.get().longValue();
    long newDelta = newInput - lastInput;
    lastInput = newInput;

    deltaSeries.addLast(newDelta);

    sumDelta += newDelta - lastDelta;

    return sumDelta;
  }
}
