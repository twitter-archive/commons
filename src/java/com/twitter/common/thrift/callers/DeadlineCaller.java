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

package com.twitter.common.thrift.callers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.google.common.base.Throwables;

import org.apache.thrift.async.AsyncMethodCallback;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.thrift.TResourceExhaustedException;
import com.twitter.common.thrift.TTimeoutException;

/**
 * A caller that imposes a time deadline on the underlying caller.  If the underlying calls fail
 * to meet the deadline {@link TTimeoutException} is thrown.  If the executor service rejects
 * execution of a task, {@link TResourceExhaustedException} is thrown.
 *
 * @author William Farner
 */
public class DeadlineCaller extends CallerDecorator {
  private final ExecutorService executorService;
  private final Amount<Long, Time> timeout;

  /**
   * Creates a new deadline caller.
   *
   * @param decoratedCaller The caller to decorate with a deadline.
   * @param async Whether the caller is asynchronous.
   * @param executorService The executor service to use for performing calls.
   * @param timeout The timeout by which the underlying call should complete in.
   */
  public DeadlineCaller(Caller decoratedCaller, boolean async, ExecutorService executorService,
      Amount<Long, Time> timeout) {
    super(decoratedCaller, async);

    this.executorService = executorService;
    this.timeout = timeout;
  }

  @Override
  public Object call(final Method method, final Object[] args,
      @Nullable final AsyncMethodCallback callback,
      @Nullable final Amount<Long, Time> connectTimeoutOverride) throws Throwable {
    try {
      Future<Object> result = executorService.submit(new Callable<Object>() {
        @Override public Object call() throws Exception {
          try {
            return invoke(method, args, callback, null, connectTimeoutOverride);
          } catch (Throwable t) {
            Throwables.propagateIfInstanceOf(t, Exception.class);
            throw new RuntimeException(t);
          }
        }
      });

      try {
        return result.get(timeout.getValue(), timeout.getUnit().getTimeUnit());
      } catch (TimeoutException e) {
        result.cancel(true);
        throw new TTimeoutException(e);
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    } catch (RejectedExecutionException e) {
      throw new TResourceExhaustedException(e);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
