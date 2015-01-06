// =================================================================================================
// Copyright 2014 Twitter, Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.Closeable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

/**
 * Low resolution implementation of a {@link com.twitter.common.util.Clock},
 * optimized for fast reads at the expense of precision.
 * It works by caching the result of the system clock for a
 * {@code resolution} amount of time.
 */
public class LowResClock implements Clock, Closeable {
  private static final ScheduledExecutorService GLOBAL_SCHEDULER =
      Executors.newScheduledThreadPool(1, new ThreadFactory() {
        public Thread newThread(Runnable r) {
          Thread t = new Thread(r, "LowResClock");
          t.setDaemon(true);
          return t;
        }
      });

  private volatile long time;
  private final ScheduledFuture<?> updaterHandler;
  private final Clock underlying;

  @VisibleForTesting
  LowResClock(Amount<Long, Time> resolution, ScheduledExecutorService executor, Clock clock) {
    long sleepTimeMs = resolution.as(Time.MILLISECONDS);
    Preconditions.checkArgument(sleepTimeMs > 0);
    underlying = clock;
    Runnable ticker = new Runnable() {
      @Override public void run() {
        time = underlying.nowMillis();
      }
    };

    // Ensure the constructing thread sees a LowResClock with a valid (low-res) time by executing a
    // blocking call now.
    ticker.run();

    updaterHandler =
        executor.scheduleAtFixedRate(ticker, sleepTimeMs, sleepTimeMs, TimeUnit.MILLISECONDS);
  }


  /**
   * Construct a LowResClock which wraps the system clock.
   * This constructor will also schedule a periodic task responsible for
   * updating the time every {@code resolution}.
   */
  public LowResClock(Amount<Long, Time> resolution) {
    this(resolution, GLOBAL_SCHEDULER, Clock.SYSTEM_CLOCK);
  }

  /**
   * Terminate the underlying updater task.
   * Any subsequent usage of the clock will throw an {@link IllegalStateException}.
   */
  public void close() {
    updaterHandler.cancel(true);
  }

  @Override
  public long nowMillis() {
    checkNotClosed();
    return time;
  }

  @Override
  public long nowNanos() {
    return nowMillis() * 1000 * 1000;
  }

  @Override
  public void waitFor(long millis) throws InterruptedException {
    checkNotClosed();
    underlying.waitFor(millis);
  }

  private void checkNotClosed() {
    if (updaterHandler.isCancelled()) {
      throw new IllegalStateException("LowResClock invoked after being closed!");
    }
  }
}
