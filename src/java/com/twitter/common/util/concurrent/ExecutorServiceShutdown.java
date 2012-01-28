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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;

import com.twitter.common.base.Command;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

/**
 * An implementation of the graceful shutdown sequence recommended by {@link ExecutorService}.
 *
 * @author John Sirois
 */
public class ExecutorServiceShutdown implements Command {
  private static final Logger LOG = Logger.getLogger(ExecutorServiceShutdown.class.getName());

  private final ExecutorService executor;
  private final Amount<Long, Time> gracePeriod;

  /**
   * Creates a new {@code ExecutorServiceShutdown} command that will try to gracefully shut down the
   * given {@code executor} when executed.  If the supplied grace period is less than or equal to
   * zero the executor service will be asked to shut down but no waiting will be done after these
   * requests.
   *
   * @param executor The executor service this command should shut down when executed.
   * @param gracePeriod The maximum time to wait after a shutdown request before continuing to the
   *     next shutdown phase.
   */
  public ExecutorServiceShutdown(ExecutorService executor, Amount<Long, Time> gracePeriod) {
    this.executor = Preconditions.checkNotNull(executor);
    this.gracePeriod = Preconditions.checkNotNull(gracePeriod);
  }

  @Override
  public void execute() {
    executor.shutdown(); // Disable new tasks from being submitted.
    try {
       // Wait a while for existing tasks to terminate.
      if (!executor.awaitTermination(gracePeriod.as(Time.MILLISECONDS), TimeUnit.MILLISECONDS)) {
        executor.shutdownNow(); // Cancel currently executing tasks.
        // Wait a while for tasks to respond to being cancelled.
        if (!executor.awaitTermination(gracePeriod.as(Time.MILLISECONDS), TimeUnit.MILLISECONDS)) {
          LOG.warning("Pool did not terminate");
        }
      }
    } catch (InterruptedException ie) {
      // (Re-)Cancel if current thread also interrupted.
      executor.shutdownNow();
      // Preserve interrupt status.
      Thread.currentThread().interrupt();
    }
  }
}
