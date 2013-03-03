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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TTransport;

import com.twitter.common.base.Closure;
import com.twitter.common.base.Closures;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.net.loadbalancing.LeastConnectedStrategy;
import com.twitter.common.net.loadbalancing.LoadBalancer;
import com.twitter.common.net.loadbalancing.LoadBalancerImpl;
import com.twitter.common.net.loadbalancing.LoadBalancingStrategy;
import com.twitter.common.net.loadbalancing.MarkDeadStrategyWithHostCheck;
import com.twitter.common.net.loadbalancing.TrafficMonitorAdapter;
import com.twitter.common.net.monitoring.TrafficMonitor;
import com.twitter.common.net.pool.Connection;
import com.twitter.common.net.pool.ConnectionPool;
import com.twitter.common.net.pool.DynamicHostSet;
import com.twitter.common.net.pool.DynamicPool;
import com.twitter.common.net.pool.MetaPool;
import com.twitter.common.net.pool.ObjectPool;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.Stats;
import com.twitter.common.stats.StatsProvider;
import com.twitter.common.thrift.ThriftConnectionFactory.TransportType;
import com.twitter.common.util.BackoffDecider;
import com.twitter.common.util.BackoffStrategy;
import com.twitter.common.util.TruncatedBinaryBackoff;
import com.twitter.common.util.concurrent.ForwardingExecutorService;
import com.twitter.thrift.ServiceInstance;

/**
 * A utility that provides convenience methods to build common {@link Thrift}s.
 *
 * The thrift factory allows you to specify parameters that define how the client connects to
 * and communicates with servers, such as the transport type, connection settings, and load
 * balancing.  Request-level settings like sync/async and retries should be set on the
 * {@link Thrift} instance that this factory will create.
 *
 * The factory will attempt to provide reasonable defaults to allow the caller to minimize the
 * amount of necessary configuration.  Currently, the default behavior includes:
 *
 * <ul>
 *   <li> A test lease/release for each host will be performed every second
 *      {@link #withDeadConnectionRestoreInterval(Amount)}
 *   <li> At most 50 connections will be established to each host
 *      {@link #withMaxConnectionsPerEndpoint(int)}
 *   <li> Unframed transport {@link #useFramedTransport(boolean)}
 *   <li> A load balancing strategy that will mark hosts dead and prefer least-connected hosts.
 *      Hosts are marked dead if the most recent connection attempt was a failure or else based on
 *      the windowed error rate of attempted RPCs.  If the error rate for a connected host exceeds
 *      20% over the last second, the host will be disabled for 2 seconds ascending up to 10 seconds
 *      if the elevated error rate persists.
 *      {@link #withLoadBalancingStrategy(LoadBalancingStrategy)}
 *   <li> Statistics are reported through {@link Stats}
 *      {@link #withStatsProvider(StatsProvider)}
 *   <li> A service name matching the thrift interface name {@link #withServiceName(String)}
 * </ul>
 *
 * @author John Sirois
 */
public class ThriftFactory<T> {
  private static final Amount<Long,Time> DEFAULT_DEAD_TARGET_RESTORE_INTERVAL =
      Amount.of(1L, Time.SECONDS);

  private static final int DEFAULT_MAX_CONNECTIONS_PER_ENDPOINT = 50;

  private Class<T> serviceInterface;
  private Function<TTransport, T> clientFactory;
  private int maxConnectionsPerEndpoint;
  private Amount<Long,Time> connectionRestoreInterval;
  private boolean framedTransport;
  private LoadBalancingStrategy<InetSocketAddress> loadBalancingStrategy = null;
  private final TrafficMonitor<InetSocketAddress> monitor;
  private Amount<Long,Time> socketTimeout = null;
  private Closure<Connection<TTransport, InetSocketAddress>> postCreateCallback = Closures.noop();
  private StatsProvider statsProvider = Stats.STATS_PROVIDER;
  private Optional<String> endpointName = Optional.absent();
  private String serviceName;
  private boolean sslTransport;

  public static <T> ThriftFactory<T> create(Class<T> serviceInterface) {
    return new ThriftFactory<T>(serviceInterface);
  }

  /**
   * Creates a default factory that will use unframed blocking transport.
   *
   * @param serviceInterface The interface of the thrift service to make a client for.
   */
  private ThriftFactory(Class<T> serviceInterface) {
    this.serviceInterface = Thrift.checkServiceInterface(serviceInterface);
    this.maxConnectionsPerEndpoint = DEFAULT_MAX_CONNECTIONS_PER_ENDPOINT;
    this.connectionRestoreInterval = DEFAULT_DEAD_TARGET_RESTORE_INTERVAL;
    this.framedTransport = false;
    this.monitor = new TrafficMonitor<InetSocketAddress>(serviceInterface.getName());
    this.serviceName = serviceInterface.getEnclosingClass().getSimpleName();
    this.sslTransport = false;
  }

  private void checkBaseState() {
    Preconditions.checkArgument(maxConnectionsPerEndpoint > 0,
        "Must allow at least 1 connection per endpoint; %s specified", maxConnectionsPerEndpoint);
  }

  public TrafficMonitor<InetSocketAddress> getMonitor() {
    return monitor;
  }

  /**
   * Creates the thrift client, and initializes connection pools.
   *
   * @param backends Backends to connect to.
   * @return A new thrift client.
   */
  public Thrift<T> build(Set<InetSocketAddress> backends) {
    checkBaseState();
    MorePreconditions.checkNotBlank(backends);

    ManagedThreadPool managedThreadPool = createManagedThreadpool(backends.size());
    LoadBalancer<InetSocketAddress> loadBalancer = createLoadBalancer();
    Function<TTransport, T> clientFactory = getClientFactory();

    ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool =
        createConnectionPool(backends, loadBalancer, managedThreadPool, false);

    return new Thrift<T>(managedThreadPool, connectionPool, loadBalancer, serviceName,
        serviceInterface, clientFactory, false, sslTransport);
  }

  /**
   * Creates a synchronous thrift client that will communicate with a dynamic host set.
   *
   * @param hostSet The host set to use as a backend.
   * @return A thrift client.
   * @throws ThriftFactoryException If an error occurred while creating the client.
   */
  public Thrift<T> build(DynamicHostSet<ServiceInstance> hostSet) throws ThriftFactoryException {
    checkBaseState();
    Preconditions.checkNotNull(hostSet);

    ManagedThreadPool managedThreadPool = createManagedThreadpool(1);
    LoadBalancer<InetSocketAddress> loadBalancer = createLoadBalancer();
    Function<TTransport, T> clientFactory = getClientFactory();

    ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool =
        createConnectionPool(hostSet, loadBalancer, managedThreadPool, false, endpointName);

    return new Thrift<T>(managedThreadPool, connectionPool, loadBalancer, serviceName,
        serviceInterface, clientFactory, false, sslTransport);
  }

  private ManagedThreadPool createManagedThreadpool(int initialEndpointCount) {
    return new ManagedThreadPool(serviceName, initialEndpointCount, maxConnectionsPerEndpoint);
  }

  /**
   * A finite thread pool that monitors backend choice events to dynamically resize.  This
   * {@link java.util.concurrent.ExecutorService} implementation immediately rejects requests when
   * there are no more available worked threads (requests are not queued).
   */
  private static class ManagedThreadPool extends ForwardingExecutorService<ThreadPoolExecutor>
      implements Closure<Collection<InetSocketAddress>> {

    private static final Logger LOG = Logger.getLogger(ManagedThreadPool.class.getName());

    private static ThreadPoolExecutor createThreadPool(String serviceName, int initialSize) {
      ThreadFactory threadFactory =
          new ThreadFactoryBuilder()
              .setNameFormat("Thrift[" +serviceName + "][%d]")
              .setDaemon(true)
              .build();
      return new ThreadPoolExecutor(initialSize, initialSize, 0, TimeUnit.MILLISECONDS,
          new SynchronousQueue<Runnable>(), threadFactory);
    }

    private final int maxConnectionsPerEndpoint;

    public ManagedThreadPool(String serviceName, int initialEndpointCount,
        int maxConnectionsPerEndpoint) {

      super(createThreadPool(serviceName, initialEndpointCount * maxConnectionsPerEndpoint));
      this.maxConnectionsPerEndpoint = maxConnectionsPerEndpoint;
      setRejectedExecutionHandler(initialEndpointCount);
    }

    private void setRejectedExecutionHandler(int endpointCount) {
      final String message =
          String.format("All %d x %d connections in use", endpointCount, maxConnectionsPerEndpoint);
      delegate.setRejectedExecutionHandler(new RejectedExecutionHandler() {
        @Override public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
          throw new RejectedExecutionException(message);
        }
      });
    }

    @Override
    public void execute(Collection<InetSocketAddress> chosenBackends) {
      int previousPoolSize = delegate.getMaximumPoolSize();
      /*
       * In the case of no available backends, we need to make sure we pass in a positive pool
       * size to our delegate.  In particular, java.util.concurrent.ThreadPoolExecutor does not
       * accept zero as a valid core or max pool size.
       */
      int backendCount = Math.max(chosenBackends.size(), 1);
      int newPoolSize = backendCount * maxConnectionsPerEndpoint;

      if (previousPoolSize != newPoolSize) {
        LOG.info(String.format("Re-sizing deadline thread pool from: %d to: %d",
            previousPoolSize, newPoolSize));
        if (previousPoolSize < newPoolSize) { // Don't cross the beams!
          delegate.setMaximumPoolSize(newPoolSize);
          delegate.setCorePoolSize(newPoolSize);
        } else {
          delegate.setCorePoolSize(newPoolSize);
          delegate.setMaximumPoolSize(newPoolSize);
        }
        setRejectedExecutionHandler(backendCount);
      }
    }
  }

  /**
   * Creates an asynchronous thrift client that will communicate with a fixed set of backends.
   *
   * @param backends Backends to connect to.
   * @return A thrift client.
   * @throws ThriftFactoryException If an error occurred while creating the client.
   */
  public Thrift<T> buildAsync(Set<InetSocketAddress> backends) throws ThriftFactoryException {
    checkBaseState();
    MorePreconditions.checkNotBlank(backends);

    LoadBalancer<InetSocketAddress> loadBalancer = createLoadBalancer();
    Closure<Collection<InetSocketAddress>> noop = Closures.noop();
    Function<TTransport, T> asyncClientFactory = getAsyncClientFactory();

    ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool =
        createConnectionPool(backends, loadBalancer, noop, true);

    return new Thrift<T>(connectionPool, loadBalancer,
        serviceName, serviceInterface, asyncClientFactory, true);
  }

  /**
   * Creates an asynchronous thrift client that will communicate with a dynamic host set.
   *
   * @param hostSet The host set to use as a backend.
   * @return A thrift client.
   * @throws ThriftFactoryException If an error occurred while creating the client.
   */
  public Thrift<T> buildAsync(DynamicHostSet<ServiceInstance> hostSet)
      throws ThriftFactoryException {
    checkBaseState();
    Preconditions.checkNotNull(hostSet);

    LoadBalancer<InetSocketAddress> loadBalancer = createLoadBalancer();
    Closure<Collection<InetSocketAddress>> noop = Closures.noop();
    Function<TTransport, T> asyncClientFactory = getAsyncClientFactory();

    ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool =
        createConnectionPool(hostSet, loadBalancer, noop, true, endpointName);

    return new Thrift<T>(connectionPool, loadBalancer,
        serviceName, serviceInterface, asyncClientFactory, true);
  }

  /**
   * Prepare the client factory, which will create client class instances from transports.
   *
   * @return The client factory to use.
   */
  private Function<TTransport, T> getClientFactory() {
    return clientFactory == null ? createClientFactory(serviceInterface) : clientFactory;
  }

  /**
   * Prepare the async client factory, which will create client class instances from transports.
   *
   * @return The client factory to use.
   * @throws ThriftFactoryException If there was a problem creating the factory.
   */
  private Function<TTransport, T> getAsyncClientFactory() throws ThriftFactoryException {
    try {
      return clientFactory == null ? createAsyncClientFactory(serviceInterface) : clientFactory;
    } catch (IOException e) {
      throw new ThriftFactoryException("Failed to create async client factory.", e);
    }
  }

  private ObjectPool<Connection<TTransport, InetSocketAddress>> createConnectionPool(
      Set<InetSocketAddress> backends, LoadBalancer<InetSocketAddress> loadBalancer,
      Closure<Collection<InetSocketAddress>> onBackendsChosen, boolean nonblocking) {

    ImmutableMap.Builder<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>
        backendBuilder = ImmutableMap.builder();
    for (InetSocketAddress backend : backends) {
      backendBuilder.put(backend, createConnectionPool(backend, nonblocking));
    }

    return new MetaPool<TTransport, InetSocketAddress>(backendBuilder.build(),
        loadBalancer, onBackendsChosen, connectionRestoreInterval);
  }

  private ObjectPool<Connection<TTransport, InetSocketAddress>> createConnectionPool(
      DynamicHostSet<ServiceInstance> hostSet, LoadBalancer<InetSocketAddress> loadBalancer,
      Closure<Collection<InetSocketAddress>> onBackendsChosen,
      final boolean nonblocking, Optional<String> serviceEndpointName)
          throws ThriftFactoryException {

    Function<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>
        endpointPoolFactory =
      new Function<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>() {
        @Override public ObjectPool<Connection<TTransport, InetSocketAddress>> apply(
            InetSocketAddress endpoint) {
          return createConnectionPool(endpoint, nonblocking);
        }
      };

    try {
      return new DynamicPool<ServiceInstance, TTransport, InetSocketAddress>(hostSet,
          endpointPoolFactory, loadBalancer, onBackendsChosen, connectionRestoreInterval,
          Util.getAddress(serviceEndpointName), Util.IS_ALIVE);
    } catch (DynamicHostSet.MonitorException e) {
      throw new ThriftFactoryException("Failed to monitor host set.", e);
    }
  }

  private ObjectPool<Connection<TTransport, InetSocketAddress>> createConnectionPool(
      InetSocketAddress backend, boolean nonblocking) {

    ThriftConnectionFactory connectionFactory = new ThriftConnectionFactory(
        backend, maxConnectionsPerEndpoint, TransportType.get(framedTransport, nonblocking),
        socketTimeout, postCreateCallback, sslTransport);

    return new ConnectionPool<Connection<TTransport, InetSocketAddress>>(connectionFactory,
        statsProvider);
  }

  @VisibleForTesting
  public ThriftFactory<T> withClientFactory(Function<TTransport, T> clientFactory) {
    this.clientFactory = Preconditions.checkNotNull(clientFactory);

    return this;
  }

  public ThriftFactory<T> withSslEnabled() {
    this.sslTransport = true;
    return this;
  }

  /**
   * Specifies the maximum number of connections that should be made to any single endpoint.
   *
   * @param maxConnectionsPerEndpoint Maximum number of connections per endpoint.
   * @return A reference to the factory.
   */
  public ThriftFactory<T> withMaxConnectionsPerEndpoint(int maxConnectionsPerEndpoint) {
    Preconditions.checkArgument(maxConnectionsPerEndpoint > 0);
    this.maxConnectionsPerEndpoint = maxConnectionsPerEndpoint;

    return this;
  }

  /**
   * Specifies the interval at which dead endpoint connections should be checked and revived.
   *
   * @param connectionRestoreInterval the time interval to check.
   * @return A reference to the factory.
   */
  public ThriftFactory<T> withDeadConnectionRestoreInterval(
      Amount<Long, Time> connectionRestoreInterval) {
    Preconditions.checkNotNull(connectionRestoreInterval);
    Preconditions.checkArgument(connectionRestoreInterval.getValue() >= 0,
        "A negative interval is invalid: %s", connectionRestoreInterval);
    this.connectionRestoreInterval = connectionRestoreInterval;

    return this;
  }

  /**
   * Instructs the factory whether framed transport should be used.
   *
   * @param framedTransport Whether to use framed transport.
   * @return A reference to the factory.
   */
  public ThriftFactory<T> useFramedTransport(boolean framedTransport) {
    this.framedTransport = framedTransport;

    return this;
  }

  /**
   * Specifies the load balancer to use when interacting with multiple backends.
   *
   * @param strategy Load balancing strategy.
   * @return A reference to the factory.
   */
  public ThriftFactory<T> withLoadBalancingStrategy(
      LoadBalancingStrategy<InetSocketAddress> strategy) {
    this.loadBalancingStrategy = Preconditions.checkNotNull(strategy);

    return this;
  }

  private LoadBalancer<InetSocketAddress> createLoadBalancer() {
    if (loadBalancingStrategy == null) {
      loadBalancingStrategy = createDefaultLoadBalancingStrategy();
    }

    return LoadBalancerImpl.create(TrafficMonitorAdapter.create(loadBalancingStrategy, monitor));
  }

  private LoadBalancingStrategy<InetSocketAddress> createDefaultLoadBalancingStrategy() {
    Function<InetSocketAddress, BackoffDecider> backoffFactory =
        new Function<InetSocketAddress, BackoffDecider>() {
          @Override public BackoffDecider apply(InetSocketAddress socket) {
            BackoffStrategy backoffStrategy = new TruncatedBinaryBackoff(
                Amount.of(2L, Time.SECONDS), Amount.of(10L, Time.SECONDS));

            return BackoffDecider.builder(socket.toString())
                .withTolerateFailureRate(0.2)
                .withRequestWindow(Amount.of(1L, Time.SECONDS))
                .withSeedSize(5)
                .withStrategy(backoffStrategy)
                .withRecoveryType(BackoffDecider.RecoveryType.FULL_CAPACITY)
                .withStatsProvider(statsProvider)
                .build();
          }
    };

    return new MarkDeadStrategyWithHostCheck<InetSocketAddress>(
        new LeastConnectedStrategy<InetSocketAddress>(), backoffFactory);
  }

  /**
   * Specifies the net read/write timeout to set via SO_TIMEOUT on the thrift blocking client
   * or AsyncClient.setTimeout on the thrift async client.  Defaults to the connectTimeout on
   * the blocking client if not set.
   *
   * @param socketTimeout timeout on thrift i/o operations
   * @return A reference to the factory.
   */
  public ThriftFactory<T> withSocketTimeout(Amount<Long, Time> socketTimeout) {
    this.socketTimeout = Preconditions.checkNotNull(socketTimeout);
    Preconditions.checkArgument(socketTimeout.as(Time.MILLISECONDS) >= 0);

    return this;
  }

  /**
   * Specifies the callback to notify when a connection has been created.  The callback may
   * be used to make thrift calls to the connection, but must not invalidate it.
   * Defaults to a no-op closure.
   *
   * @param postCreateCallback function to setup new connections
   * @return A reference to the factory.
   */
  public ThriftFactory<T> withPostCreateCallback(
      Closure<Connection<TTransport, InetSocketAddress>> postCreateCallback) {
    this.postCreateCallback = Preconditions.checkNotNull(postCreateCallback);

    return this;
  }

  /**
   * Registers a custom stats provider to use to track various client stats.
   *
   * @param statsProvider the {@code StatsProvider} to use
   * @return A reference to the factory.
   */
  public ThriftFactory<T> withStatsProvider(StatsProvider statsProvider) {
    this.statsProvider = Preconditions.checkNotNull(statsProvider);

    return this;
  }

  /**
   * Name to be passed to Thrift constructor, used in stats.
   *
   * @param serviceName string to use
   * @return A reference to the factory.
   */
  public ThriftFactory<T> withServiceName(String serviceName) {
    this.serviceName = MorePreconditions.checkNotBlank(serviceName);

    return this;
  }

  /**
   * Set the end-point to use from {@link ServiceInstance#getAdditionalEndpoints()}.
   * If not set, the default behavior is to use {@link ServiceInstance#getServiceEndpoint()}.
   *
   * @param endpointName the (optional) name of the end-point, if unset - the
   *     default/primary end-point is selected
   * @return a reference to the factory for chaining
   */
  public ThriftFactory<T> withEndpointName(String endpointName) {
    this.endpointName = Optional.of(endpointName);
    return this;
  }

  private static <T> Function<TTransport, T> createClientFactory(Class<T> serviceInterface) {
    final Constructor<? extends T> implementationConstructor =
        findImplementationConstructor(serviceInterface);

    return new Function<TTransport, T>() {
      @Override public T apply(TTransport transport) {
        try {
          return implementationConstructor.newInstance(new TBinaryProtocol(transport));
        } catch (InstantiationException e) {
          throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private <T> Function<TTransport, T> createAsyncClientFactory(
      final Class<T> serviceInterface) throws IOException {

    final TAsyncClientManager clientManager = new TAsyncClientManager();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override public void run() {
        clientManager.stop();
      }
    });

    final Constructor<? extends T> implementationConstructor =
        findAsyncImplementationConstructor(serviceInterface);

    return new Function<TTransport, T>() {
      @Override public T apply(TTransport transport) {
        Preconditions.checkNotNull(transport);
        Preconditions.checkArgument(transport instanceof TNonblockingTransport,
            "Invalid transport provided to client factory: " + transport.getClass());

        try {
          T client = implementationConstructor.newInstance(new TBinaryProtocol.Factory(),
              clientManager, transport);

          if (socketTimeout != null) {
            ((TAsyncClient) client).setTimeout(socketTimeout.as(Time.MILLISECONDS));
          }

          return client;
        } catch (InstantiationException e) {
          throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private static <T> Constructor<? extends T> findImplementationConstructor(
      final Class<T> serviceInterface) {
    Class<? extends T> implementationClass = findImplementationClass(serviceInterface);
    try {
      return implementationClass.getConstructor(TProtocol.class);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Failed to find a single argument TProtocol constructor "
                                         + "in service client class: " + implementationClass);
    }
  }

  private static <T> Constructor<? extends T> findAsyncImplementationConstructor(
      final Class<T> serviceInterface) {
    Class<? extends T> implementationClass = findImplementationClass(serviceInterface);
    try {
      return implementationClass.getConstructor(TProtocolFactory.class, TAsyncClientManager.class,
          TNonblockingTransport.class);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Failed to find expected constructor "
                                         + "in service client class: " + implementationClass);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<? extends T> findImplementationClass(final Class<T> serviceInterface) {
    try {
      return (Class<? extends T>)
          Iterables.find(ImmutableList.copyOf(serviceInterface.getEnclosingClass().getClasses()),
              new Predicate<Class<?>>() {
                @Override public boolean apply(Class<?> inner) {
                  return !serviceInterface.equals(inner)
                         && serviceInterface.isAssignableFrom(inner);
                }
              });
    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException("Could not find a sibling enclosed implementation of "
                                         + "service interface: " + serviceInterface);
    }
  }

  public static class ThriftFactoryException extends Exception {
    public ThriftFactoryException(String msg) {
      super(msg);
    }

    public ThriftFactoryException(String msg, Throwable t) {
      super(msg, t);
    }
  }
}
