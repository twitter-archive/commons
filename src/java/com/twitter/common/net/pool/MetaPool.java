// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.net.pool;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.twitter.common.base.Closure;
import com.twitter.common.base.Command;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.net.loadbalancing.LoadBalancer;
import com.twitter.common.net.loadbalancing.LoadBalancingStrategy.ConnectionResult;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A connection pool that picks connections from a set of backend pools.  Backend pools are selected
 * from randomly initially but then as they are used they are ranked according to how many
 * connections they have available and whether or not the last used connection had an error or not.
 * In this way, backends that are responsive should get selected in preference to those that are
 * not.
 *
 * <p>Non-responsive backends are monitored after a configurable period in a background thread and
 * if a connection can be obtained they start to float back up in the rankings.  In this way,
 * backends that are initially non-responsive but later become responsive should end up getting
 * selected.
 *
 * <p> TODO(John Sirois): take a ShutdownRegistry and register a close command
 *
 * @author John Sirois
 */
public class MetaPool<T, E> implements ObjectPool<Connection<T, E>> {

  private final Command stopCommand;

  private Map<E, ObjectPool<Connection<T, E>>> backends = null;
  private final LoadBalancer<E> loadBalancer;
  private final Closure<Collection<E>> onBackendsChosen;

  /**
   * Creates a connection pool with no backends.  Backends may be added post-creation by calling
   * {@link #setBackends<ImmutableSet>()};
   *
   * @param loadBalancer the load balancer to distribute requests among backends.
   * @param onBackendsChosen a callback to notify whenever the {@code loadBalancer} chooses a new
   *     set of backends to restrict its call distribution to
   * @param restoreInterval the interval after a backend goes dead to begin checking the backend to
   *     see if it has come back to a healthy state
   */
  public MetaPool(LoadBalancer<E> loadBalancer,
      Closure<Collection<E>> onBackendsChosen, Amount<Long, Time> restoreInterval) {
    this(ImmutableMap.<E, ObjectPool<Connection<T, E>>>of(), loadBalancer,
        onBackendsChosen, restoreInterval);
  }

  /**
   * Creates a connection pool that balances connections across multiple backend pools.
   *
   * @param backends the connection pools for the backends
   * @param onBackendsChosen a callback to notify whenever the {@code loadBalancer} chooses a new
   *     set of backends to restrict its call distribution to
   * @param loadBalancer the load balancer to distribute requests among backends.
   * @param restoreInterval the interval after a backend goes dead to begin checking the backend to
   *     see if it has come back to a healthy state
   */
  public MetaPool(
      ImmutableMap<E, ObjectPool<Connection<T, E>>> backends,
      LoadBalancer<E> loadBalancer,
      Closure<Collection<E>> onBackendsChosen, Amount<Long, Time> restoreInterval) {

    this.loadBalancer = Preconditions.checkNotNull(loadBalancer);
    this.onBackendsChosen = Preconditions.checkNotNull(onBackendsChosen);
    setBackends(backends);

    Preconditions.checkNotNull(restoreInterval);
    Preconditions.checkArgument(restoreInterval.getValue() > 0);
    stopCommand = startDeadBackendRestorer(restoreInterval);
  }

  /**
   * Assigns the backend pools that this pool should draw from.
   *
   * @param pools New pools to use.
   */
  public synchronized void setBackends(Map<E, ObjectPool<Connection<T, E>>> pools) {
    backends = Preconditions.checkNotNull(pools);
    loadBalancer.offerBackends(pools.keySet(), onBackendsChosen);
  }

  private Command startDeadBackendRestorer(final Amount<Long, Time> restoreInterval) {

    final AtomicBoolean shouldRestore = new AtomicBoolean(true);
    Runnable restoreDeadBackends = new Runnable() {
      @Override public void run() {
        if (shouldRestore.get()) {
          restoreDeadBackends(restoreInterval);
        }
      }
    };
    final ScheduledExecutorService scheduledExecutorService =
        Executors.newScheduledThreadPool(1,
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("MTCP-DeadBackendRestorer[%s]")
                .build());
    long restoreDelay = restoreInterval.getValue();
    scheduledExecutorService.scheduleWithFixedDelay(restoreDeadBackends, restoreDelay,
        restoreDelay, restoreInterval.getUnit().getTimeUnit());

    return new Command() {
      @Override public void execute() {
        shouldRestore.set(false);
        scheduledExecutorService.shutdownNow();
        LOG.info("Backend restorer shut down");
      }
    };
  }

  private static final Logger LOG = Logger.getLogger(MetaPool.class.getName());

  private synchronized void restoreDeadBackends(Amount<Long, Time> restoreInterval) {
    for (E backend : backends.keySet()) {
      try {
        release(get(backend, restoreInterval));
      } catch (TimeoutException e) {
        LOG.warning("Backend restorer failed to revive backend: " + backend + " -> " + e);
      } catch (ResourceExhaustedException e) {
        LOG.warning("Backend restorer failed to revive backend: " + backend + " -> " + e);
      }
    }
  }

  @Override
  public Connection<T, E> get() throws ResourceExhaustedException, TimeoutException {
    return get(ObjectPool.NO_TIMEOUT);
  }

  @Override
  public Connection<T, E> get(Amount<Long, Time> timeout)
      throws ResourceExhaustedException, TimeoutException {

    return get(loadBalancer.nextBackend(), timeout);
  }

  private static class ManagedConnection<T, E> implements Connection<T, E> {
    private final Connection<T, E> connection;
    private final ObjectPool<Connection<T, E>> pool;

    private ManagedConnection(Connection<T, E> connection, ObjectPool<Connection<T, E>> pool) {
      this.connection = connection;
      this.pool = pool;
    }

    @Override
    public void close() {
      connection.close();
    }

    @Override
    public T get() {
      return connection.get();
    }

    @Override
    public boolean isValid() {
      return connection.isValid();
    }

    @Override
    public E getEndpoint() {
      return connection.getEndpoint();
    }

    @Override public String toString() {
      return "ManagedConnection[" + connection.toString() + "]";
    }

    void release(boolean remove) {
      if (remove) {
        pool.remove(connection);
      } else {
        pool.release(connection);
      }
    }
  }

  private Connection<T, E> get(E backend, Amount<Long, Time> timeout)
      throws ResourceExhaustedException, TimeoutException {
    long startNanos = System.nanoTime();

    ObjectPool<Connection<T, E>> pool = Preconditions.checkNotNull(backends.get(backend));

    try {
      Connection<T, E> connection = (timeout.getValue() == 0) ? pool.get() : pool.get(timeout);

      // BEWARE: We have leased a connection from the underlying pool here and must return it to the
      // caller so they can later release it.  If we fail to do so, the connection will leak.
      // Catching intermediate exceptions ourselves and pro-actively returning the connection to the
      // pool before re-throwing is not a viable option since the return would have to succeed,
      // forcing us to ignore the timeout passed in.

      try {
        loadBalancer.connected(backend, System.nanoTime() - startNanos);
      } catch (RuntimeException e) {
        LOG.log(Level.WARNING, "Encountered an exception updating load balancer stats after "
                               + "leasing a connection - continuing", e);
      }
      return new ManagedConnection<T, E>(connection, pool);
    } catch (TimeoutException e) {
      loadBalancer.connectFailed(backend, ConnectionResult.TIMEOUT);
      throw e;
    } catch (ResourceExhaustedException e) {
      loadBalancer.connectFailed(backend, ConnectionResult.FAILED);
      throw e;
    }
  }

  // Locks to guard mutation of the backends set.
  private final ReadWriteLock backendsLock = new ReentrantReadWriteLock(true);
  private final Lock backendsReadLock = backendsLock.readLock();
  private final Lock backendsWriteLock = backendsLock.writeLock();

  @Override
  public void release(Connection<T, E> connection) {
    release(connection, false);
  }

  /**
   * Equivalent to releasing a Connection with isValid() == false.
   * @see {@link ObjectPool#remove(Object)}
   */
  @Override
  public void remove(Connection<T, E> connection) {
    release(connection, true);
  }

  private void release(Connection<T, E> connection, boolean remove) {
    backendsWriteLock.lock();
    try {

      if (!(connection instanceof ManagedConnection)) {
        throw new IllegalArgumentException("Connection not controlled by this connection pool: "
                                           + connection);
      }
      ((ManagedConnection) connection).release(remove);

      loadBalancer.released(connection.getEndpoint());
    } finally {
      backendsWriteLock.unlock();
    }
  }

  @Override
  public synchronized void close() {
    stop();

    backendsReadLock.lock();
    try {
      for (ObjectPool<Connection<T, E>> backend : backends.values()) {
        backend.close();
      }
    } finally {
      backendsReadLock.unlock();
    }
  }

  /**
   * Stops dead backend restoration attempts.
   *
   * <p>TODO(John Sirois): stop functionality is needed to properly implement a close that frees all
   * pool resources; however having to expose it for subclasses is a hack that solely supports
   * ServerSetConnectionPool - this might be made cleaner by injecting the dead pool restorer
   * instead.
   */
  protected final void stop() {
    stopCommand.execute();
  }
}
