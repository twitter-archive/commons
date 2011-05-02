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

import com.google.common.base.Preconditions;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

/**
 * A BackoffStrategy that implements truncated binary exponential backoff.
 */
public class TruncatedBinaryBackoff implements BackoffStrategy {
  private final long initialBackoffMs;
  private final long maxBackoffIntervalMs;

  /**
   * Creates a new TruncatedBinaryBackoff that will start by backing off for {@code initialBackoff}
   * and then backoff of twice as long each time its called until reaching the {@code maxBackoff} at
   * which point it will always wait for that amount of time.
   *
   * @param initialBackoff the intial amount of time to backoff
   * @param maxBackoff the maximum amount of time to backoff
   */
  public TruncatedBinaryBackoff(Amount<Long, Time> initialBackoff,
      Amount<Long, Time> maxBackoff) {
    Preconditions.checkNotNull(initialBackoff);
    Preconditions.checkNotNull(maxBackoff);
    Preconditions.checkArgument(initialBackoff.getValue() > 0);
    Preconditions.checkArgument(maxBackoff.compareTo(initialBackoff) >= 0);
    initialBackoffMs = initialBackoff.as(Time.MILLISECONDS);
    maxBackoffIntervalMs = maxBackoff.as(Time.MILLISECONDS);
  }

  @Override
  public long calculateBackoffMs(long lastBackoffMs) {
    Preconditions.checkArgument(lastBackoffMs >= 0);
    return (lastBackoffMs == 0) ? initialBackoffMs
        : Math.min(maxBackoffIntervalMs, lastBackoffMs * 2);
  }
}
