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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A future task that supports retries by resubmitting itself to an {@link ExecutorService}.
 *
 * @author William Farner
 */
public class RetryingFutureTask extends FutureTask<Boolean> {
  private static Logger LOG = Logger.getLogger(RetryingFutureTask.class.getName());

  protected final ExecutorService executor;
  protected final int maxRetries;
  protected int numRetries = 0;
  protected final Callable<Boolean> callable;

  /**
   * Creates a new retrying future task that will execute a unit of work until successfully
   * completed, or the retry limit has been reached.
   *
   * @param executor The executor service to resubmit the task to upon failure.
   * @param callable The unit of work.  The work is considered successful when {@code true} is
   *    returned.  It may return {@code false} or throw an exception when unsueccessful.
   * @param maxRetries The maximum number of times to retry the task.
   */
  public RetryingFutureTask(ExecutorService executor, Callable<Boolean> callable, int maxRetries) {
    super(callable);
    this.callable = Preconditions.checkNotNull(callable);
    this.executor = Preconditions.checkNotNull(executor);
    this.maxRetries = maxRetries;
  }

  /**
   * Invokes a retry of this task.
   */
  protected void retry() {
    executor.execute(this);
  }

  @Override
  public void run() {
    boolean success = false;
    try {
      success = callable.call();
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Exception while executing task.", e);
    }

    if (!success) {
      numRetries++;
      if (numRetries > maxRetries) {
        LOG.severe("Task did not complete after " + maxRetries + " retries, giving up.");
      } else {
        LOG.info("Task was not successful, resubmitting (num retries: " + numRetries + ")");
        retry();
      }
    } else {
      set(true);
    }
  }
}
