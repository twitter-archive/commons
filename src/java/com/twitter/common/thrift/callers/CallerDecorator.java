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

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import org.apache.thrift.async.AsyncMethodCallback;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
* A caller that decorates another caller.
*
* @author William Farner
*/
abstract class CallerDecorator implements Caller {
  private final Caller decoratedCaller;
  private final boolean async;

  CallerDecorator(Caller decoratedCaller, boolean async) {
    this.decoratedCaller = decoratedCaller;
    this.async = async;
  }

  /**
   * Convenience method for invoking the method and shunting the capture into the callback if
   * the call is asynchronous.
   *
   * @param method The method being invoked.
   * @param args The arguments to call {@code method} with.
   * @param callback The callback to use if the method is asynchronous.
   * @param capture The result capture to notify of the call result.
   * @param connectTimeoutOverride Optional override for the default connection timeout.
   * @return The return value from invoking the method.
   * @throws Throwable Exception, as prescribed by the method's contract.
   */
  protected final Object invoke(Method method, Object[] args,
      @Nullable AsyncMethodCallback callback, @Nullable final ResultCapture capture,
      @Nullable Amount<Long, Time> connectTimeoutOverride) throws Throwable {

    // Swap the wrapped callback out for ours.
    if (callback != null) {
      callback = new WrappedMethodCallback(callback, capture);
    }

    try {
      Object result = decoratedCaller.call(method, args, callback, connectTimeoutOverride);
      if (callback == null && capture != null) capture.success();

      return result;
    } catch (Throwable t) {
      // We allow this one to go to both sync and async captures.
      if (callback != null) {
        callback.onError(t);
        return null;
      } else {
        if (capture != null) capture.fail(t);
        throw t;
      }
    }
  }

  boolean isAsync() {
    return async;
  }
}
