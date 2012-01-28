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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;

import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

/**
 * CommandExecutor that invokes {@code queueDrainer} with a best-effort
 * mechanism to execute with a fixed interval between requests of {@code
 * intervalBetweenRequests}.
 *
 * @author Srinivasan Rajagopal
 */
public class RateLimitedCommandExecutor implements CommandExecutor {

  private static final Logger LOG = Logger.getLogger(RateLimitedCommandExecutor.class.getName());

  final private BlockingQueue<RetryingRunnable> blockingQueue;

  /**
   * Create a CommandExecutor that executes enquequed tasks in the task
   * executor with specified interval between executions.
   *
   * @param taskExecutor executor for periodic execution of enqueued tasks.
   * @param intervalBetweenRequests interval between requests to rate limit
   * request rate.
   */
  public RateLimitedCommandExecutor(ScheduledExecutorService taskExecutor,
      Amount<Long, Time> intervalBetweenRequests, Runnable queueDrainer,
      BlockingQueue<RetryingRunnable> blockingQueue) {
    Preconditions.checkNotNull(taskExecutor);
    Preconditions.checkNotNull(intervalBetweenRequests);
    Preconditions.checkArgument(intervalBetweenRequests.as(Time.MILLISECONDS) > 0);
    Preconditions.checkNotNull(queueDrainer);
    this.blockingQueue = Preconditions.checkNotNull(blockingQueue);
    taskExecutor.scheduleWithFixedDelay(
        getSafeRunner(queueDrainer),
        0,
        intervalBetweenRequests.as(Time.MILLISECONDS),
        TimeUnit.MILLISECONDS);
  }

  private static Runnable getSafeRunner(final Runnable runnable) {
    return new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        } catch (RuntimeException t) {
          LOG.log(Level.INFO, " error processing task " + runnable);
        }
      }
    };
  }

  @Override
  public void execute(String name, ExceptionalCommand task,
      Class exceptionClass, int numTries, Amount<Long, Time> retryDelay) {
    blockingQueue.add(new RetryingRunnable(name, task, exceptionClass,
        numTries, retryDelay, this));
  }
}
