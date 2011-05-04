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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.twitter.common.net.pool.Connection;
import com.twitter.common.net.pool.ObjectPool;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.thrift.TResourceExhaustedException;
import com.twitter.common.thrift.TTimeoutException;
import com.twitter.common.net.loadbalancing.RequestTracker;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.transport.TTransport;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * A caller that issues calls to a target that is assumed to be a client to a thrift service.
 *
 * @author William Farner
 */
public class ThriftCaller<T> implements Caller {
  private static final Logger LOG = Logger.getLogger(ThriftCaller.class.getName());

  private final ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool;
  private final RequestTracker<InetSocketAddress> requestTracker;
  private final Function<TTransport, T> clientFactory;
  private final Amount<Long, Time> timeout;
  private final boolean debug;

  /**
   * Creates a new thrift caller.
   *
   * @param connectionPool The connection pool to use.
   * @param requestTracker The request tracker to nofify of request results.
   * @param clientFactory Factory to use for building client object instances.
   * @param timeout The timeout to use when requesting objects from the connection pool.
   * @param debug Whether to use the caller in debug mode.
   */
  public ThriftCaller(ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool,
      RequestTracker<InetSocketAddress> requestTracker, Function<TTransport, T> clientFactory,
      Amount<Long, Time> timeout, boolean debug) {

    this.connectionPool = connectionPool;
    this.requestTracker = requestTracker;
    this.clientFactory = clientFactory;
    this.timeout = timeout;
    this.debug = debug;
  }

  @Override
  public Object call(Method method, Object[] args, @Nullable AsyncMethodCallback callback,
      @Nullable Amount<Long, Time> connectTimeoutOverride) throws Throwable {

    final Connection<TTransport, InetSocketAddress> connection = getConnection(connectTimeoutOverride);
    final long startNanos = System.nanoTime();

    ResultCapture capture = new ResultCapture() {
      @Override public void success() {
        try {
          requestTracker.requestResult(connection.getEndpoint(),
              RequestTracker.RequestResult.SUCCESS, System.nanoTime() - startNanos);
        } finally {
          connectionPool.release(connection);
        }
      }

      @Override public boolean fail(Throwable t) {
        if (debug) {
          LOG.warning(String.format("Call to endpoint: %s failed: %s", connection, t));
        }

        try {
          requestTracker.requestResult(connection.getEndpoint(),
              RequestTracker.RequestResult.FAILED, System.nanoTime() - startNanos);
        } finally {
          connectionPool.remove(connection);
        }
        return true;
      }
    };

    return invokeMethod(clientFactory.apply(connection.get()), method, args, callback, capture);
  }

  private static Object invokeMethod(Object target, Method method, Object[] args,
      AsyncMethodCallback callback, final ResultCapture capture) throws Throwable {

    // Swap the wrapped callback out for ours.
    if (callback != null) {
      callback = new WrappedMethodCallback(callback, capture);

      List<Object> argsList = Lists.newArrayList(args);
      argsList.add(callback);
      args = argsList.toArray();
    }

    try {
      Object result = method.invoke(target, args);
      if (callback == null) capture.success();

      return result;
    } catch (InvocationTargetException t) {
      // We allow this one to go to both sync and async captures.
      if (callback != null) {
        callback.onError(t.getCause());
        return null;
      } else {
        capture.fail(t.getCause());
        throw t.getCause();
      }
    }
  }

  private Connection<TTransport, InetSocketAddress> getConnection(
      Amount<Long, Time> connectTimeoutOverride)
      throws TResourceExhaustedException, TTimeoutException {
    try {
      Connection<TTransport, InetSocketAddress> connection;
      if (connectTimeoutOverride != null) {
        connection = connectionPool.get(connectTimeoutOverride);
      } else {
        connection = (timeout.getValue() > 0)
            ? connectionPool.get(timeout) : connectionPool.get();
      }

      if (connection == null) {
        throw new TResourceExhaustedException("no connection was available");
      }
      return connection;
    } catch (ResourceExhaustedException e) {
      throw new TResourceExhaustedException(e);
    } catch (TimeoutException e) {
      throw new TTimeoutException(e);
    }
  }
}
