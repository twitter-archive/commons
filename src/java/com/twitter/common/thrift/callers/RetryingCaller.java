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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.apache.thrift.async.AsyncMethodCallback;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.StatsProvider;
import com.twitter.common.thrift.TResourceExhaustedException;

/**
* A caller that will retry calls to the wrapped caller.
*
* @author William Farner
*/
public class RetryingCaller extends CallerDecorator {
  private static final Logger LOG = Logger.getLogger(RetryingCaller.class.getName());

  @VisibleForTesting
  public static final Amount<Long, Time> NONBLOCKING_TIMEOUT = Amount.of(-1L, Time.MILLISECONDS);

  private final StatsProvider statsProvider;
  private final String serviceName;
  private final int retries;
  private final ImmutableSet<Class<? extends Exception>> retryableExceptions;
  private final boolean debug;

  /**
   * Creates a new retrying caller. The retrying caller will attempt to call invoked methods on the
   * underlying caller at most {@code retries} times.  A retry will be performed only when one of
   * the {@code retryableExceptions} is caught.
   *
   * @param decoratedCall The caller to decorate with retries.
   * @param async Whether the caller is asynchronous.
   * @param statsProvider The stat provider to export retry statistics through.
   * @param serviceName The service name that calls are being invoked on.
   * @param retries The maximum number of retries to perform.
   * @param retryableExceptions The exceptions that can be retried.
   * @param debug Whether to include debugging information when retries are being performed.
   */
  public RetryingCaller(Caller decoratedCall, boolean async, StatsProvider statsProvider,
      String serviceName, int retries, ImmutableSet<Class<? extends Exception>> retryableExceptions,
      boolean debug) {
    super(decoratedCall, async);
    this.statsProvider = statsProvider;
    this.serviceName = serviceName;
    this.retries = retries;
    this.retryableExceptions = retryableExceptions;
    this.debug = debug;
  }

  private final LoadingCache<Method, AtomicLong> stats =
      CacheBuilder.newBuilder().build(new CacheLoader<Method, AtomicLong>() {
        @Override public AtomicLong load(Method method) {
          // Thrift does not support overloads - so just the name disambiguates all calls.
          return statsProvider.makeCounter(serviceName + "_" + method.getName() + "_retries");
        }
      });

  @Override public Object call(final Method method, final Object[] args,
      @Nullable final AsyncMethodCallback callback,
      @Nullable final Amount<Long, Time> connectTimeoutOverride) throws Throwable {
    final AtomicLong retryCounter = stats.get(method);
    final AtomicInteger attempts = new AtomicInteger();
    final List<Throwable> exceptions = Lists.newArrayList();

    final ResultCapture capture = new ResultCapture() {
      @Override public void success() {
        // No-op.
      }

      @Override public boolean fail(Throwable t) {
        if (!isRetryable(t)) {
          if (debug) {
            LOG.warning(String.format(
                "Call failed with un-retryable exception of [%s]: %s, previous exceptions: %s",
                t.getClass().getName(), t.getMessage(), combineStackTraces(exceptions)));
          }

          return true;
        } else if (attempts.get() >= retries) {
          exceptions.add(t);

          if (debug) {
            LOG.warning(String.format("Retried %d times, last error: %s, exceptions: %s",
                attempts.get(), t, combineStackTraces(exceptions)));
          }

          return true;
        } else {
          exceptions.add(t);

          if (isAsync() && attempts.incrementAndGet() <= retries) {
            try {
              retryCounter.incrementAndGet();
              // override connect timeout in ThriftCaller to prevent blocking for a connection
              // for async retries (since this is within the callback in the selector thread)
              invoke(method, args, callback, this, NONBLOCKING_TIMEOUT);
            } catch (Throwable throwable) {
              return fail(throwable);
            }
          }

          return false;
        }
      }
    };

    boolean continueLoop;
    do {
      try {
        // If this is an async call, the looping will be handled within the capture.
        return invoke(method, args, callback, capture, connectTimeoutOverride);
      } catch (Throwable t) {
        if (!isRetryable(t)) {
          Throwable propagated = t;

          if (!exceptions.isEmpty() && (t instanceof TResourceExhaustedException)) {
            // If we've been trucking along through retries that have had remote call failures
            // and we suddenly can't immediately get a connection on the next retry, throw the
            // previous remote call failure - the idea here is that the remote call failure is
            // more interesting than a transient inability to get an immediate connection.
            propagated = exceptions.remove(exceptions.size() - 1);
          }

          if (isAsync()) {
            callback.onError(propagated);
          } else {
            throw propagated;
          }
        }
      }

      continueLoop = !isAsync() && attempts.incrementAndGet() <= retries;
      if (continueLoop) retryCounter.incrementAndGet();
    } while (continueLoop);

    Throwable lastRetriedException = Iterables.getLast(exceptions);
    if (debug) {
      if (!exceptions.isEmpty()) {
        LOG.warning(
            String.format("Retried %d times, last error: %s, previous exceptions: %s",
                attempts.get(), lastRetriedException, combineStackTraces(exceptions)));
      } else {
        LOG.warning(
            String.format("Retried 1 time, last error: %s", lastRetriedException));
      }
    }

    if (!isAsync()) throw lastRetriedException;
    return null;
  }

  private boolean isRetryable(Throwable throwable) {
    return isRetryable.getUnchecked(throwable.getClass());
  }

  private final LoadingCache<Class<? extends Throwable>, Boolean> isRetryable =
      CacheBuilder.newBuilder().build(new CacheLoader<Class<? extends Throwable>, Boolean>() {
        @Override public Boolean load(Class<? extends Throwable> exceptionClass) {
          return isRetryable(exceptionClass);
        }
      });

  private boolean isRetryable(final Class<? extends Throwable> exceptionClass) {
    if (retryableExceptions.contains(exceptionClass)) {
      return true;
    }
    return Iterables.any(retryableExceptions, new Predicate<Class<? extends Exception>>() {
      @Override public boolean apply(Class<? extends Exception> retryableExceptionClass) {
        return retryableExceptionClass.isAssignableFrom(exceptionClass);
      }
    });
  }

  private static final Joiner STACK_TRACE_JOINER = Joiner.on('\n');

  private static String combineStackTraces(List<Throwable> exceptions) {
    if (exceptions.isEmpty()) {
      return "none";
    } else {
      return STACK_TRACE_JOINER.join(Iterables.transform(exceptions,
          new Function<Throwable, String>() {
            private int index = 1;
            @Override public String apply(Throwable exception) {
              return String.format("[%d] %s",
                  index++, Throwables.getStackTraceAsString(exception));
            }
          }));
    }
  }
}
