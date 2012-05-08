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

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.stats.StatsProvider;

import static org.easymock.EasyMock.expect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * TODO(William Farner): Test async.
 *
 * @author William Farner
 */
public class RetryingCallerTest extends AbstractCallerTest {

  private static final int NUM_RETRIES = 2;

  private static final ImmutableSet<Class<? extends Exception>> NO_RETRYABLE =
      ImmutableSet.of();
  private static final ImmutableSet<Class<? extends Exception>> RETRYABLE =
      ImmutableSet.<Class<? extends Exception>>of(IllegalArgumentException.class);

  private StatsProvider statsProvider;

  @Before
  public void mySetUp() {
    statsProvider = createMock(StatsProvider.class);
  }

  @Test
  public void testSuccess() throws Throwable {
    expectCall("foo");

    control.replay();

    RetryingCaller retry = makeRetry(false, NO_RETRYABLE);
    assertThat(call(retry), is("foo"));
    assertThat(memoizeGetCounter.get(methodA).get(), is(0L));
  }

  @Test
  public void testException() throws Throwable {
    Throwable exception = nonRetryable();
    expectCall(exception);

    control.replay();

    RetryingCaller retry = makeRetry(false, NO_RETRYABLE);
    try {
      call(retry);
      fail();
    } catch (Throwable t) {
      assertThat(t, is(exception));
    }
    assertThat(memoizeGetCounter.get(methodA).get(), is(0L));
  }

  @Test
  public void testRetriesSuccess() throws Throwable {
    expectCall(retryable());
    expectCall(retryable());
    expectCall("foo");

    control.replay();

    RetryingCaller retry = makeRetry(false, RETRYABLE);
    assertThat(call(retry), is("foo"));
    assertThat(memoizeGetCounter.get(methodA).get(), is((long) NUM_RETRIES));
  }

  @Test
  public void testRetryLimit() throws Throwable {
    expectCall(retryable());
    expectCall(retryable());
    Throwable exception = retryable();
    expectCall(exception);

    control.replay();

    RetryingCaller retry = makeRetry(false, RETRYABLE);
    try {
      call(retry);
      fail();
    } catch (Throwable t) {
      assertThat(t, is(exception));
    }
    assertThat(memoizeGetCounter.get(methodA).get(), is(2L));
  }

  private Throwable retryable() {
    return new IllegalArgumentException();
  }

  private Throwable nonRetryable() {
    return new NullPointerException();
  }

  private LoadingCache<Method, AtomicLong> memoizeGetCounter = CacheBuilder.newBuilder().build(
      new CacheLoader<Method, AtomicLong>() {
        @Override public AtomicLong load(Method method) {
          AtomicLong atomicLong = new AtomicLong();
          expect(statsProvider.makeCounter("test_" + method.getName() + "_retries"))
              .andReturn(atomicLong);
          return atomicLong;
        }
      });

  @Override
  protected void expectCall(String returnValue) throws Throwable {
    super.expectCall(returnValue);
    memoizeGetCounter.get(methodA);
  }

  @Override
  protected void expectCall(Throwable thrown) throws Throwable {
    super.expectCall(thrown);
    memoizeGetCounter.get(methodA);
  }

  private RetryingCaller makeRetry(boolean async,
      ImmutableSet<Class<? extends Exception>> retryableExceptions) {
    return new RetryingCaller(caller, async, statsProvider, "test", NUM_RETRIES,
        retryableExceptions, false);
  }
}
