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

package com.twitter.common.thrift;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import com.twitter.common.base.MorePreconditions;
import com.twitter.common.net.loadbalancing.RequestTracker;
import com.twitter.common.net.pool.Connection;
import com.twitter.common.net.pool.ObjectPool;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.StatsProvider;
import com.twitter.common.thrift.callers.Caller;
import com.twitter.common.thrift.callers.DeadlineCaller;
import com.twitter.common.thrift.callers.DebugCaller;
import com.twitter.common.thrift.callers.RetryingCaller;
import com.twitter.common.thrift.callers.StatTrackingCaller;
import com.twitter.common.thrift.callers.ThriftCaller;

/**
 * A generic thrift client that handles reconnection in the case of protocol errors, automatic
 * retries, call deadlines and call statistics tracking.  This class aims for behavior compatible
 * with the <a href="http://github.com/fauna/thrift_client">generic ruby thrift client</a>.
 *
 * <p>In order to enforce call deadlines for synchronous clients, this class uses an
 * {@link java.util.concurrent.ExecutorService}.  If a custom executor is supplied, it should throw
 * a subclass of {@link RejectedExecutionException} to signal thread resource exhaustion, in which
 * case the client will fail fast and propagate the event as a {@link TResourceExhaustedException}.
 *
 * TODO(William Farner): Before open sourcing, look into changing the current model of wrapped proxies
 *    to use a single proxy and wrapped functions for decorators.
 *
 * @author John Sirois
 */
public class Thrift<T> {

  /**
   * The default thrift call configuration used if none is specified.
   *
   * Specifies the following settings:
   * <ul>
   * <li>global call timeout: 1 second
   * <li>call retries: 0
   * <li>retryable exceptions: TTransportException (network exceptions including socket timeouts)
   * <li>wait for connections: true
   * <li>debug: false
   * </ul>
   */
  public static final Config DEFAULT_CONFIG = Config.builder()
      .withRequestTimeout(Amount.of(1L, Time.SECONDS))
      .noRetries()
      .retryOn(TTransportException.class) // if maxRetries is set non-zero
      .create();

  /**
   * The default thrift call configuration used for an async client if none is specified.
   *
   * Specifies the following settings:
   * <ul>
   * <li>global call timeout: none
   * <li>call retries: 0
   * <li>retryable exceptions: IOException, TTransportException
   *    (network exceptions but not timeouts)
   * <li>wait for connections: true
   * <li>debug: false
   * </ul>
   */
  @SuppressWarnings("unchecked")
  public static final Config DEFAULT_ASYNC_CONFIG = Config.builder(DEFAULT_CONFIG)
      .withRequestTimeout(Amount.of(0L, Time.SECONDS))
      .noRetries()
      .retryOn(ImmutableSet.<Class<? extends Exception>>builder()
          .add(IOException.class)
          .add(TTransportException.class).build()) // if maxRetries is set non-zero
      .create();

  private final Config defaultConfig;
  private final ExecutorService executorService;
  private final ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool;
  private final RequestTracker<InetSocketAddress> requestTracker;
  private final String serviceName;
  private final Class<T> serviceInterface;
  private final Function<TTransport, T> clientFactory;
  private final boolean async;
  private final boolean withSsl;

  /**
   * Constructs an instance with the {@link #DEFAULT_CONFIG}, cached thread pool
   * {@link ExecutorService}, and synchronous calls.
   *
   * @see #Thrift(Config, ExecutorService, ObjectPool, RequestTracker , String, Class, Function,
   *     boolean, boolean)
   */
  public Thrift(ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool,
      RequestTracker<InetSocketAddress> requestTracker,
      String serviceName, Class<T> serviceInterface, Function<TTransport, T> clientFactory) {

    this(DEFAULT_CONFIG, connectionPool, requestTracker, serviceName, serviceInterface,
        clientFactory, false, false);
  }

  /**
   * Constructs an instance with the {@link #DEFAULT_CONFIG} and cached thread pool
   * {@link ExecutorService}.
   *
   * @see #Thrift(Config, ExecutorService, ObjectPool, RequestTracker , String, Class, Function,
   *    boolean, boolean)
   */
  public Thrift(ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool,
      RequestTracker<InetSocketAddress> requestTracker,
      String serviceName, Class<T> serviceInterface, Function<TTransport, T> clientFactory,
      boolean async) {

    this(getConfig(async), connectionPool, requestTracker, serviceName,
        serviceInterface, clientFactory, async, false);
  }

  /**
   * Constructs an instance with the {@link #DEFAULT_CONFIG} and cached thread pool
   * {@link ExecutorService}.
   *
   * @see #Thrift(Config, ExecutorService, ObjectPool, RequestTracker , String, Class, Function,
   *    boolean, boolean)
   */
  public Thrift(ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool,
      RequestTracker<InetSocketAddress> requestTracker,
      String serviceName, Class<T> serviceInterface, Function<TTransport, T> clientFactory,
      boolean async, boolean ssl) {

    this(getConfig(async), connectionPool, requestTracker, serviceName,
        serviceInterface, clientFactory, async, ssl);
  }

  /**
   * Constructs an instance with a cached thread pool {@link ExecutorService}.
   *
   * @see #Thrift(Config, ExecutorService, ObjectPool, RequestTracker , String, Class, Function,
   *    boolean, boolean)
   */
  public Thrift(Config config, ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool,
      RequestTracker<InetSocketAddress> requestTracker,
      String serviceName, Class<T> serviceInterface, Function<TTransport, T> clientFactory,
      boolean async, boolean ssl) {

    this(config,
        Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("Thrift["+ serviceName +"][%d]")
                .build()),
        connectionPool, requestTracker, serviceName, serviceInterface, clientFactory, async, ssl);
  }

  /**
   * Constructs an instance with the {@link #DEFAULT_CONFIG}.
   *
   * @see #Thrift(Config, ExecutorService, ObjectPool, RequestTracker , String, Class, Function,
   *    boolean, boolean)
   */
  public Thrift(ExecutorService executorService,
      ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool,
      RequestTracker<InetSocketAddress> requestTracker,
      String serviceName, Class<T> serviceInterface, Function<TTransport, T> clientFactory,
      boolean async, boolean ssl) {

    this(getConfig(async), executorService, connectionPool, requestTracker, serviceName,
        serviceInterface, clientFactory, async, ssl);
  }

  private static Config getConfig(boolean async) {
    return async ? DEFAULT_ASYNC_CONFIG : DEFAULT_CONFIG;
  }

  /**
   * Constructs a new Thrift factory for creating clients that make calls to a particular thrift
   * service.
   *
   * <p>Note that the combination of {@code config} and {@code connectionPool} need to be chosen
   * with care depending on usage of the generated thrift clients.  In particular, if configured
   * to not wait for connections, the {@code connectionPool} ought to be warmed up with a set of
   * connections or else be actively building connections in the background.
   *
   * <p>TODO(John Sirois): consider adding an method to ObjectPool that would allow Thrift to handle
   * this case by pro-actively warming the pool.
   *
   * @param config the default configuration to use for all thrift calls; also the configuration all
   *     {@link ClientBuilder}s start with
   * @param executorService for invoking calls with a specified deadline
   * @param connectionPool the source for thrift connections
   * @param serviceName a /vars friendly name identifying the service clients will connect to
   * @param serviceInterface the thrift compiler generate interface class for the remote service
   *     (Iface)
   * @param clientFactory a function that can generate a concrete thrift client for the given
   *     {@code serviceInterface}
   * @param async enable asynchronous API
   * @param ssl enable TLS handshaking for Thrift calls
   */
  public Thrift(Config config, ExecutorService executorService,
      ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool,
      RequestTracker<InetSocketAddress> requestTracker, String serviceName,
      Class<T> serviceInterface, Function<TTransport, T> clientFactory, boolean async, boolean ssl) {

    defaultConfig = Preconditions.checkNotNull(config);
    this.executorService = Preconditions.checkNotNull(executorService);
    this.connectionPool = Preconditions.checkNotNull(connectionPool);
    this.requestTracker = Preconditions.checkNotNull(requestTracker);
    this.serviceName = MorePreconditions.checkNotBlank(serviceName);
    this.serviceInterface = checkServiceInterface(serviceInterface);
    this.clientFactory = Preconditions.checkNotNull(clientFactory);
    this.async = async;
    this.withSsl = ssl;
  }

  static <I> Class<I> checkServiceInterface(Class<I> serviceInterface) {
    Preconditions.checkNotNull(serviceInterface);
    Preconditions.checkArgument(serviceInterface.isInterface(),
        "%s must be a thrift service interface", serviceInterface);
    return serviceInterface;
  }

  /**
   * Closes any open connections and prepares this thrift client for graceful shutdown.  Any thrift
   * client proxies returned from {@link #create()} will become invalid.
   */
  public void close() {
    connectionPool.close();
    executorService.shutdown();
  }

  /**
   * A builder class that allows modifications of call behavior to be made for a given Thrift
   * client.  Note that in the case of conflicting configuration calls, the last call wins.  So,
   * for example, the following sequence would result in all calls being subject to a 5 second
   * global deadline:
   * <code>
   *   builder.blocking().withDeadline(5, TimeUnit.SECONDS).create()
   * </code>
   *
   * @see Config
   */
  public final class ClientBuilder extends Config.AbstractBuilder<ClientBuilder> {
    private ClientBuilder(Config template) {
      super(template);
    }

    @Override
    protected ClientBuilder getThis() {
      return this;
    }

    /**
     * Creates a new client using the built up configuration changes.
     */
    public T create() {
      return createClient(getConfig());
    }
  }

  /**
   * Creates a new thrift client builder that inherits this Thrift instance's default configuration.
   * This is useful for customizing a client for a particular thrift call that makes sense to treat
   * differently from the rest of the calls to a given service.
   */
  public ClientBuilder builder() {
    return builder(defaultConfig);
  }

  /**
   * Creates a new thrift client builder that inherits the given configuration.
   * This is useful for customizing a client for a particular thrift call that makes sense to treat
   * differently from the rest of the calls to a given service.
   */
  public ClientBuilder builder(Config config) {
    Preconditions.checkNotNull(config);
    return new ClientBuilder(config);
  }

  /**
   * Creates a new client using the default configuration specified for this Thrift instance.
   */
  public T create() {
    return createClient(defaultConfig);
  }

  private T createClient(Config config) {
    StatsProvider statsProvider = config.getStatsProvider();

    // lease/call/[invalidate]/release
    boolean debug = config.isDebug();

    Caller decorated = new ThriftCaller<T>(connectionPool, requestTracker, clientFactory,
        config.getConnectTimeout(), debug);

    // [retry]
    if (config.getMaxRetries() > 0) {
      decorated = new RetryingCaller(decorated, async, statsProvider, serviceName,
          config.getMaxRetries(), config.getRetryableExceptions(), debug);
    }

    // [deadline]
    if (config.getRequestTimeout().getValue() > 0) {
      Preconditions.checkArgument(!async,
          "Request deadlines may not be used with an asynchronous client.");

      decorated = new DeadlineCaller(decorated, async, executorService, config.getRequestTimeout());
    }

    // [debug]
    if (debug) {
      decorated = new DebugCaller(decorated, async);
    }

    // stats
    if (config.enableStats()) {
      decorated = new StatTrackingCaller(decorated, async, statsProvider, serviceName);
    }

    final Caller caller = decorated;

    final InvocationHandler invocationHandler = new InvocationHandler() {
      @Override
      public Object invoke(Object o, Method method, Object[] args) throws Throwable {
        AsyncMethodCallback callback = null;
        if (args != null && async) {
          List<Object> argsList = Lists.newArrayList(args);
          callback = extractCallback(argsList);
          args = argsList.toArray();
        }

        return caller.call(method, args, callback, null);
      }
    };

    @SuppressWarnings("unchecked")
    T instance = (T) Proxy.newProxyInstance(serviceInterface.getClassLoader(),
        new Class<?>[] {serviceInterface}, invocationHandler);
    return instance;
  }

  /**
   * Verifies that the final argument in a list of objects is a fully-formed
   * {@link AsyncMethodCallback} and extracts it, removing it from the argument list.
   *
   * @param args Argument list to remove the callback from.
   * @return The callback extracted from {@code args}.
   */
  private static AsyncMethodCallback extractCallback(List<Object> args) {
    // TODO(William Farner): Check all interface methods when building the Thrift client
    //    and verify that last arguments are all callbacks...this saves us from checking
    //    each time.

    // Check that the last argument is a callback.
    Preconditions.checkArgument(args.size() > 0);
    Object lastArg = args.get(args.size() - 1);
    Preconditions.checkArgument(lastArg instanceof AsyncMethodCallback,
        "Last argument of an async thrift call is expected to be of type AsyncMethodCallback.");

    return (AsyncMethodCallback) args.remove(args.size() - 1);
  }
}
