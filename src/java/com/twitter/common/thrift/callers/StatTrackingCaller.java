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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.apache.thrift.async.AsyncMethodCallback;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.StatsProvider;
import com.twitter.common.stats.StatsProvider.RequestTimer;
import com.twitter.common.thrift.TResourceExhaustedException;
import com.twitter.common.thrift.TTimeoutException;

/**
 * A caller that exports statistics about calls made to the wrapped caller.
 *
 * @author William Farner
 */
public class StatTrackingCaller extends CallerDecorator {

  private final StatsProvider statsProvider;
  private final String serviceName;

  private final LoadingCache<Method, RequestTimer> stats =
      CacheBuilder.newBuilder().build(new CacheLoader<Method, RequestTimer>() {
        @Override public RequestTimer load(Method method) {
          // Thrift does not support overloads - so just the name disambiguates all calls.
          return statsProvider.makeRequestTimer(serviceName + "_" + method.getName());
        }
      });

  /**
   * Creates a new stat tracking caller, which will export stats to the given {@link StatsProvider}.
   *
   * @param decoratedCaller The caller to decorate with a deadline.
   * @param async Whether the caller is asynchronous.
   * @param statsProvider The stat provider to export statistics to.
   * @param serviceName The name of the service that methods are being called on.
   */
  public StatTrackingCaller(Caller decoratedCaller, boolean async, StatsProvider statsProvider,
      String serviceName) {
    super(decoratedCaller, async);

    this.statsProvider = statsProvider;
    this.serviceName = serviceName;
  }

  @Override
  public Object call(Method method, Object[] args, @Nullable AsyncMethodCallback callback,
      @Nullable Amount<Long, Time> connectTimeoutOverride) throws Throwable {
    final RequestTimer requestStats = stats.get(method);
    final long startTime = System.nanoTime();

    ResultCapture capture = new ResultCapture() {
      @Override public void success() {
        requestStats.requestComplete(TimeUnit.NANOSECONDS.toMicros(
            System.nanoTime() - startTime));
      }

      @Override public boolean fail(Throwable t) {
        // TODO(John Sirois): the ruby client reconnects for timeouts too - this provides a natural
        // backoff mechanism - consider how to plumb something similar.
        if (t instanceof TTimeoutException || t instanceof TimeoutException) {
          requestStats.incTimeouts();
          return true;
        }

        // TODO(John Sirois): consider ditching reconnects since its nearly redundant with errors as
        // it stands.
        if (!(t instanceof TResourceExhaustedException)) {
          requestStats.incReconnects();
        }
        // TODO(John Sirois): provide more detailed stats: track counts for distinct exceptions types,
        // track retries-per-method, etc...
        requestStats.incErrors();
        return true;
      }
    };

    return invoke(method, args, callback, capture, connectTimeoutOverride);
  }
}
