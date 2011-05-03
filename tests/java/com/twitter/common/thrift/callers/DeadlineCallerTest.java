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

import com.google.common.testing.TearDown;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.thrift.TTimeoutException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * TODO(William Farner): Test async.
 *
 * @author William Farner
 */
public class DeadlineCallerTest extends AbstractCallerTest {

  private static final Amount<Long, Time> DEADLINE = Amount.of(100L, Time.MILLISECONDS);

  private ExecutorService executorService;

  private DeadlineCaller makeDeadline(final boolean shouldTimeOut) {
    final CountDownLatch cancelled = new CountDownLatch(1);
    if (shouldTimeOut) {
      addTearDown(new TearDown() {
        @Override public void tearDown() throws Exception {
          // This will block forever if cancellation does not occur and interrupt the ~indefinite
          // sleep.
          cancelled.await();
        }
      });
    }

    Caller sleepyCaller = new CallerDecorator(caller, false) {
      @Override public Object call(Method method, Object[] args,
          @Nullable AsyncMethodCallback callback,
          @Nullable Amount<Long, Time> connectTimeoutOverride) throws Throwable {

        if (shouldTimeOut) {
          try {
            Thread.sleep(Long.MAX_VALUE);
            fail("Expected late work to be cancelled and interrupted");
          } catch (InterruptedException e) {
            cancelled.countDown();
          }
        }

        return caller.call(method, args, callback, connectTimeoutOverride);
      }
    };

    return new DeadlineCaller(sleepyCaller, false, executorService, DEADLINE);
  }

  @Before
  public void setUp() {
    executorService = Executors.newSingleThreadExecutor();
  }

  @Test
  public void testSuccess() throws Throwable {
    DeadlineCaller deadline = makeDeadline(false);
    expectCall("foo");

    control.replay();

    assertThat(call(deadline), is("foo"));
  }

  @Test
  public void testException() throws Throwable {
    DeadlineCaller deadline = makeDeadline(false);
    Throwable exception = new IllegalArgumentException();
    expectCall(exception);

    control.replay();

    try {
      call(deadline);
      fail();
    } catch (Throwable t) {
      assertThat(t, is(exception));
    }
  }

  @Test(expected = TTimeoutException.class)
  public void testExceedsDeadline() throws Throwable {
    DeadlineCaller deadline = makeDeadline(true);

    // No call expected, since we time out before it can be made.

    control.replay();

    call(deadline);
  }
}
