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

package com.twitter.common.util.concurrent;

import com.google.common.base.Preconditions;
import com.twitter.common.util.BackoffStrategy;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A {@link RetryingFutureTask} that will resubmit itself to a work queue with a backoff.
 *
 * @author William Farner
 */
public class BackingOffFutureTask extends RetryingFutureTask {
  private final ScheduledExecutorService executor;
  private final BackoffStrategy backoffStrategy;
  private long backoffMs = 0;

  /**
   * Creates a new retrying future task that will execute a unit of work until successfully
   * completed, or the retry limit has been reached.
   *
   * @param executor   The executor service to resubmit the task to upon failure.
   * @param callable   The unit of work.  The work is considered successful when {@code true} is
   *                   returned.  It may return {@code false} or throw an exception when
   *                   unsueccessful.
   * @param maxRetries The maximum number of times to retry the task.
   * @param backoffStrategy Strategy to use for determining backoff duration.
   */
  public BackingOffFutureTask(ScheduledExecutorService executor, Callable<Boolean> callable,
      int maxRetries, BackoffStrategy backoffStrategy) {
    super(executor, callable, maxRetries);
    this.executor = executor;
    this.backoffStrategy = Preconditions.checkNotNull(backoffStrategy);
  }

  @Override
  protected void retry() {
    backoffMs = backoffStrategy.calculateBackoffMs(backoffMs);
    executor.schedule(this, backoffMs, TimeUnit.MILLISECONDS);
  }
}
