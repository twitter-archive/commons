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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Throwables;

import org.apache.commons.lang.builder.ToStringBuilder;

import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A runnable task that is retried in a user-configurable fashion.
 *
 * @param <E> The type of exception that the ExceptionalCommand throws.
 *
 * @author Utkarsh Srivastava
 */
public class RetryingRunnable<E extends Exception> implements Runnable {
  private final String name;
  private final int tryNum;
  private final int numTries;
  private final Amount<Long, Time> retryDelay;
  private final ExceptionalCommand<E> task;
  private final CommandExecutor commandExecutor;
  private final Class<E> exceptionClass;

  private static final Logger LOG = Logger.getLogger(RetryingRunnable.class.getName());

  /**
   * Create a Task with name {@code name} that executes at most {@code  numTries}
   * in case of failure with an interval of {@code retryDelay} between attempts.
   *
   * @param name Human readable name for this task.
   * @param task the task to execute.
   * @param exceptionClass class of the exception thrown by the task.
   * @param numTries the total number of times to try.
   * @param retryDelay the delay between successive tries.
   * @param commandExecutor Executor to resubmit retries to.
   * @param tryNum the seq number of this try.
   */
  public RetryingRunnable(
      String name,
      ExceptionalCommand<E> task,
      Class<E> exceptionClass,
      int numTries,
      Amount<Long, Time> retryDelay,
      CommandExecutor commandExecutor,
      int tryNum) {

    this.name = checkNotNull(name);
    this.task = checkNotNull(task);
    this.exceptionClass = checkNotNull(exceptionClass);
    this.retryDelay = checkNotNull(retryDelay);
    this.commandExecutor = checkNotNull(commandExecutor);
    checkArgument(numTries > 0);
    this.tryNum = tryNum;
    this.numTries = numTries;
  }

  /**
   * Create a Task with name {@code name} that executes at most {@code numTries}
   * in case of failure with an interval of {@code retryDelay} between attempts
   *  and sets tryNum to be the first (=1).
   *
   * @param name Human readable name for this task.
   * @param task the task to execute.
   * @param exceptionClass class of the exception thrown by the task.
   * @param numTries the total number of times to try.
   * @param retryDelay the delay between successive tries.
   * @param commandExecutor Executor to resubmit retries to.
   */
  public RetryingRunnable(
      String name,
      ExceptionalCommand<E> task,
      Class<E> exceptionClass,
      int numTries,
      Amount<Long, Time> retryDelay,
      CommandExecutor commandExecutor) {

    this(name, task, exceptionClass, numTries, retryDelay, commandExecutor, /*tryNum=*/ 1);
  }

  @Override
  public void run() {
    try {
      task.execute();
    } catch (Exception e) {
      if (e.getClass().isAssignableFrom(exceptionClass)) {
        if (tryNum < numTries) {
          commandExecutor.execute(name, task, exceptionClass, numTries - 1, retryDelay);
        } else {
          LOG.log(Level.INFO, "Giving up on task: " + name + " "
              + "after " + "trying " + numTries + " times" + ".", e);
        }
      } else {
        LOG.log(Level.INFO, "Giving up on task: " + name + " after trying "
            + numTries + " times. " + "due to unhandled exception ", e);
        throw Throwables.propagate(e);
      }
    }
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("name", name)
        .append("tryNum", tryNum)
        .append("numTries", numTries)
        .append("retryDelay", retryDelay)
        .toString();
  }
}
