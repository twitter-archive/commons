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
import java.util.concurrent.Executor;

import com.google.common.base.Preconditions;

/**
 * Joins a task queue with an executor service, to add control over when
 * tasks are actually made available for execution.
 *
 * @author Srinivasan Rajagopal
 */
public class QueueDrainer<T extends Runnable> implements Runnable {

  private final Executor taskExecutor;
  private final BlockingQueue<T> blockingQueue;

  /**
   * Creates a QueueDrainer that associates the queue with an executorService.
   *
   * @param taskExecutor Executor to execute a task if present.
   * @param blockingQueue Queue to poll if there is a runnable to execute.
   */
  public QueueDrainer(Executor taskExecutor, BlockingQueue<T> blockingQueue) {
    this.taskExecutor = Preconditions.checkNotNull(taskExecutor);
    this.blockingQueue = Preconditions.checkNotNull(blockingQueue);
  }

  /**
   * Picks tasks from the Queue to execute if present else no-op.
   */
  @Override
  public void run() {
    Runnable command = blockingQueue.poll();
    if (command != null) {
      taskExecutor.execute(command);
    }
  }
}
