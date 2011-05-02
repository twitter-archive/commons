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

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import org.apache.thrift.async.AsyncMethodCallback;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * A caller that reports debugging information about calls.
 *
 * @author William Farner
 */
public class DebugCaller extends CallerDecorator {
  private static final Logger LOG = Logger.getLogger(DebugCaller.class.getName());
  private static final Joiner ARG_JOINER = Joiner.on(", ");

  /**
   * Creates a new debug caller.
   *
   * @param decoratedCaller The caller to decorate with debug information.
   * @param async Whether the caller is asynchronous.
   */
  public DebugCaller(Caller decoratedCaller, boolean async) {
    super(decoratedCaller, async);
  }

  @Override
  public Object call(final Method method, final Object[] args,
      @Nullable AsyncMethodCallback callback, @Nullable Amount<Long, Time> connectTimeoutOverride)
      throws Throwable {
    ResultCapture capture = new ResultCapture() {
      @Override public void success() {
        // No-op.
      }

      @Override public boolean fail(Throwable t) {
        StringBuilder message = new StringBuilder("Thrift call failed: ");
        message.append(method.getName()).append("(");
        ARG_JOINER.appendTo(message, args);
        message.append(")");
        LOG.warning(message.toString());

        return true;
      }
    };

    try {
      return invoke(method, args, callback, capture, connectTimeoutOverride);
    } catch (Throwable t) {
      capture.fail(t);
      throw t;
    }
  }
}
