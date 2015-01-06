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

package com.twitter.common.util.testing;

import com.google.common.base.Preconditions;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

import java.util.concurrent.TimeUnit;

/**
 * A clock for use in testing with a configurable value for {@link #nowMillis()}.
 *
 * @author John Sirois
 */
public class FakeClock implements Clock {
  // Tests may need to use the clock from multiple threads, ensure liveness.
  private volatile long nowNanos;

  /**
   * Sets what {@link #nowMillis()} will return until this method is called again with a new value
   * for {@code now}.
   *
   * @param nowMillis the current time in milliseconds
   */
  public void setNowMillis(long nowMillis) {
    Preconditions.checkArgument(nowMillis >= 0);
    this.nowNanos = TimeUnit.MILLISECONDS.toNanos(nowMillis);
  }

  /**
   * Advances the current time by {@code millis} milliseconds.  Time can be retarded by passing a
   * negative value.
   *
   * @param period the amount of time to advance the current time by
   */
  public void advance(Amount<Long, Time> period) {
    Preconditions.checkNotNull(period);
    long newNanos = nowNanos + period.as(Time.NANOSECONDS);
    Preconditions.checkArgument(newNanos >= 0,
        "invalid period %s - would move current time to a negative value: %sns", period, newNanos);
    nowNanos = newNanos;
  }

  @Override
  public long nowMillis() {
    return TimeUnit.NANOSECONDS.toMillis(nowNanos);
  }

  @Override
  public long nowNanos() {
    return nowNanos;
  }

  /**
   * Waits in fake time, immediately returning in real time; however a check of {@link #nowMillis}
   * after this method completes will consistently reveal that {@code millis} did in fact pass while
   * waiting.
   *
   * @param millis the amount of time to wait in milliseconds
   */
  @Override
  public void waitFor(long millis) {
    advance(Amount.of(millis, Time.MILLISECONDS));
  }
}
