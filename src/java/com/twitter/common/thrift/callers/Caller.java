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

import com.google.common.base.Preconditions;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import org.apache.thrift.async.AsyncMethodCallback;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
* A caller that invokes a method on an object.
*
* @author William Farner
*/
public interface Caller {

  /**
   * Invokes a method on an object, using the given arguments.  The method call may be
   * asynchronous, in which case {@code callback} will be non-null.
   *
   * @param method The method being invoked.
   * @param args The arguments to call {@code method} with.
   * @param callback The callback to use if the method is asynchronous.
   * @param connectTimeoutOverride Optional override for the default connection timeout.
   * @return The return value from invoking the method.
   * @throws Throwable Exception, as prescribed by the method's contract.
   */
  public Object call(Method method, Object[] args, @Nullable AsyncMethodCallback callback,
      @Nullable Amount<Long, Time> connectTimeoutOverride) throws Throwable;

  /**
   * Captures the result of a request, whether synchronous or asynchronous.  It should be expected
   * that for every request made, exactly one of these methods will be called.
   */
  static interface ResultCapture {
    /**
     * Called when the request completed successfully.
     */
    void success();

    /**
     * Called when the request failed.
     *
     * @param t Throwable that was caught.  Must never be null.
     * @return {@code true} if a wrapped callback should be notified of the failure,
     *    {@code false} otherwise.
     */
    boolean fail(Throwable t);
  }

  /**
   * A callback that adapts a {@link ResultCapture} with an {@link AsyncMethodCallback} while
   * maintaining the AsyncMethodCallback interface.  The wrapped callback will handle invocation
   * of the underlying callback based on the return values from the ResultCapture.
   */
  static class WrappedMethodCallback implements AsyncMethodCallback {
    private final AsyncMethodCallback wrapped;
    private final ResultCapture capture;

    private boolean callbackTriggered = false;

    public WrappedMethodCallback(AsyncMethodCallback wrapped, ResultCapture capture) {
      this.wrapped = wrapped;
      this.capture = capture;
    }

    private void callbackTriggered() {
      Preconditions.checkState(!callbackTriggered, "Each callback may only be triggered once.");
      callbackTriggered = true;
    }

    @Override @SuppressWarnings("unchecked") public void onComplete(Object o) {
      capture.success();
      wrapped.onComplete(o);
      callbackTriggered();
    }

    @Override public void onError(Throwable t) {
      if (capture.fail(t)) {
        wrapped.onError(t);
        callbackTriggered();
      }
    }
  }
}
