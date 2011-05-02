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

package com.twitter.common.util;

import com.twitter.common.base.Commands;
import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.stats.SlidingStats;

/**
 * A utility for timing blocks of code.
 *
 * <p>TODO(John Sirois): consider instead:
 * <T, E extends Exception> Pair<T, Long> doTimed(ExceptionalSupplier<T, E> timedWork) throws E
 * or a subinterface of Command/Closure/Supplier/Function that exposes a timing method as other ways
 * to factor in timing.
 *
 * @author John Sirois
 */
public final class Timer {

  /**
   * Times the block of code encapsulated by {@code timedWork} recoding the result in {@code stat}.
   *
   * @param stat the stat to record the timing with
   * @param timedWork the code to time
   * @param <E> the type of exception {@code timedWork} may throw
   * @throws E if {@code timedWork} throws
   */
  public static <E extends Exception> void doTimed(SlidingStats stat,
      final ExceptionalCommand<E> timedWork) throws E {
    doTimed(stat, Commands.asSupplier(timedWork));
  }

  /**
   * Times the block of code encapsulated by {@code timedWork} recoding the result in {@code stat}.
   *
   * @param stat the stat to record the timing with
   * @param timedWork the code to time
   * @param <T> the type of result {@code timedWork} returns
   * @param <E> the type of exception {@code timedWork} may throw
   * @return the result of {@code timedWork} if it completes normally
   * @throws E if {@code timedWork} throws
   */
  public static <T, E extends Exception> T doTimed(SlidingStats stat,
      ExceptionalSupplier<T, E> timedWork) throws E {
    StartWatch timer = new StartWatch();
    timer.start();
    try {
      return timedWork.get();
    } finally {
      timer.stop();
      stat.accumulate(timer.getTime());
    }
  }

  private Timer() {
    // utility
  }
}
