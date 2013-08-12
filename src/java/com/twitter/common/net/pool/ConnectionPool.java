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

package com.twitter.common.net.pool;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.twitter.common.base.Supplier;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.Stats;
import com.twitter.common.stats.StatsProvider;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A generic connection pool that delegates growth policy to a {@link ConnectionFactory} and
 * connection choice to a supplied strategy.
 *
 * <p>TODO(John Sirois): implement a reaper to clean up connections that may become invalid when not in
 * use.
 *
 * <p> TODO(John Sirois): take a ShutdownRegistry and register a close command
 *
 * @author John Sirois
 */
public final class ConnectionPool<S extends Connection<?, ?>> implements ObjectPool<S> {

  private static final Logger LOG = Logger.getLogger(ConnectionPool.class.getName());

  private final Set<S> leasedConnections =
      Sets.newSetFromMap(Maps.<S, Boolean>newIdentityHashMap());
  private final Set<S> availableConnections = Sets.newHashSet();
  private final Lock poolLock;
  private final Condition available;

  private final ConnectionFactory<S> connectionFactory;
  private final Executor executor;

  private volatile boolean closed;
  private final AtomicLong connectionsCreated;
  private final AtomicLong connectionsDestroyed;
  private final AtomicLong connectionsReturned;

  /**
   * Creates a connection pool with a connection picker that selects the first item in the set of
   * available connections, exporting statistics to stats provider {@link Stats#STATS_PROVIDER}.
   *
   * @param connectionFactory Factory to create and destroy connections.
   */
  public ConnectionPool(ConnectionFactory<S> connectionFactory) {
    this(connectionFactory, Stats.STATS_PROVIDER);
  }

  /**
   * Creates a connection pool with a connection picker that selects the first item in the set of
   * available connections and uses the supplied StatsProvider to register stats with.
   *
   * @param connectionFactory Factory to create and destroy connections.
   * @param statsProvider Stats export provider.
   */
  public ConnectionPool(ConnectionFactory<S> connectionFactory, StatsProvider statsProvider) {
    this(Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setNameFormat("CP-" + connectionFactory + "[%d]")
            .setDaemon(true)
            .build()),
        new ReentrantLock(true), connectionFactory, statsProvider);
  }

  @VisibleForTesting
  ConnectionPool(Executor executor, Lock poolLock, ConnectionFactory<S> connectionFactory,
      StatsProvider statsProvider) {
    Preconditions.checkNotNull(executor);
    Preconditions.checkNotNull(poolLock);
    Preconditions.checkNotNull(connectionFactory);
    Preconditions.checkNotNull(statsProvider);

    this.executor = executor;
    this.poolLock = poolLock;
    available = poolLock.newCondition();
    this.connectionFactory = connectionFactory;

    String cfName = Stats.normalizeName(connectionFactory.toString());
    statsProvider.makeGauge("cp_leased_connections_" + cfName,
        new Supplier<Integer>() {
      @Override public Integer get() {
        return leasedConnections.size();
      }
    });
    statsProvider.makeGauge("cp_available_connections_" + cfName,
        new Supplier<Integer>() {
          @Override public Integer get() {
            return availableConnections.size();
          }
        });
    this.connectionsCreated =
        statsProvider.makeCounter("cp_created_connections_" + cfName);
    this.connectionsDestroyed =
        statsProvider.makeCounter("cp_destroyed_connections_" + cfName);
    this.connectionsReturned =
        statsProvider.makeCounter("cp_returned_connections_" + cfName);
  }

  @Override
  public String toString() {
    return "CP-" + connectionFactory;
  }

  @Override
  public S get() throws ResourceExhaustedException, TimeoutException {
    checkNotClosed();
    poolLock.lock();
    try {
      return leaseConnection(NO_TIMEOUT);
    } finally {
      poolLock.unlock();
    }
  }

  @Override
  public S get(Amount<Long, Time> timeout)
      throws ResourceExhaustedException, TimeoutException {

    checkNotClosed();
    Preconditions.checkNotNull(timeout);
    if (timeout.getValue() == 0) {
      return get();
    }

    try {
      long start = System.nanoTime();
      long timeBudgetNs = timeout.as(Time.NANOSECONDS);
      if (poolLock.tryLock(timeBudgetNs, TimeUnit.NANOSECONDS)) {
        try {
          timeBudgetNs -= (System.nanoTime() - start);
          return leaseConnection(Amount.of(timeBudgetNs, Time.NANOSECONDS));
        } finally {
          poolLock.unlock();
        }
      } else {
        throw new TimeoutException("Timed out waiting for pool lock");
      }
    } catch (InterruptedException e) {
      throw new TimeoutException("Interrupted waiting for pool lock");
    }
  }

  private S leaseConnection(Amount<Long, Time> timeout) throws ResourceExhaustedException,
      TimeoutException {
    S connection = getConnection(timeout);
    if (connection == null) {
      throw new ResourceExhaustedException("Connection pool resources exhausted");
    }
    return leaseConnection(connection);
  }

  @Override
  public void release(S connection) {
    release(connection, false);
  }

  /**
   * Equivalent to releasing a Connection with isValid() == false.
   * @see ObjectPool#remove(Object)
   */
  @Override
  public void remove(S connection) {
    release(connection, true);
  }

  // TODO(John Sirois): release could block indefinitely if someone is blocked in get() on a create
  // connection - reason about this and potentially submit release to our executor
  private void release(S connection, boolean remove) {
    poolLock.lock();
    try {
      if (!leasedConnections.remove(connection)) {
        throw new IllegalArgumentException("Connection not controlled by this connection pool: "
                                           + connection);
      }

      if (!closed && !remove && connection.isValid()) {
        addConnection(connection);
        connectionsReturned.incrementAndGet();
      } else {
        connectionFactory.destroy(connection);
        connectionsDestroyed.incrementAndGet();
      }
    } finally {
      poolLock.unlock();
    }
  }

  @Override
  public void close() {
    poolLock.lock();
    try {
      for (S availableConnection : availableConnections) {
        connectionFactory.destroy(availableConnection);
      }
    } finally {
      closed = true;
      poolLock.unlock();
    }
  }

  private void checkNotClosed() {
    Preconditions.checkState(!closed);
  }

  private S leaseConnection(S connection) {
    leasedConnections.add(connection);
    return connection;
  }

  // TODO(John Sirois): pool growth is serialized by poolLock currently - it seems like this could be
  // fixed but there may be no need - do gedankanalysis
  private S getConnection(final Amount<Long, Time> timeout) throws ResourceExhaustedException,
      TimeoutException {
    if (availableConnections.isEmpty()) {
      if (leasedConnections.isEmpty()) {
        // Completely empty pool
        try {
          return createConnection(timeout);
        } catch (Exception e) {
          throw new ResourceExhaustedException("failed to create a new connection", e);
        }
      } else {
        // If the pool is allowed to grow - let the connection factory race a release
        if (connectionFactory.mightCreate()) {
          executor.execute(new Runnable() {
            @Override public void run() {
              try {
                // The connection timeout is not needed here to honor the callers get requested
                // timeout, but we don't want to have an infinite timeout which could exhaust a
                // thread pool over many backgrounded create calls
                S connection = createConnection(timeout);
                if (connection != null) {
                  addConnection(connection);
                } else {
                  LOG.log(Level.WARNING, "Failed to create a new connection for a waiting client " +
                      "due to maximum pool size or timeout");
                }
              } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to create a new connection for a waiting client", e);
              }
            }
          });
        }

        try {
          // We wait for a returned/new connection here in loops to guard against the
          // "spurious wakeups" that are documented can occur with Condition.await()
          if (timeout.getValue() == 0) {
            while(availableConnections.isEmpty()) {
              available.await();
            }
          } else {
            long timeRemainingNs = timeout.as(Time.NANOSECONDS);
            while(availableConnections.isEmpty()) {
              long start = System.nanoTime();
              if (!available.await(timeRemainingNs, TimeUnit.NANOSECONDS)) {
                throw new TimeoutException(
                    "timeout waiting for a connection to be released to the pool");
              } else {
                timeRemainingNs -= (System.nanoTime() - start);
              }
            }
            if (availableConnections.isEmpty()) {
              throw new TimeoutException(
                  "timeout waiting for a connection to be released to the pool");
            }
          }
        } catch (InterruptedException e) {
          throw new TimeoutException("Interrupted while waiting for a connection.");
        }
      }
    }

    return getAvailableConnection();
  }

  private S getAvailableConnection() {
    S connection = (availableConnections.size() == 1)
        ? Iterables.getOnlyElement(availableConnections)
        : availableConnections.iterator().next();
    if (!availableConnections.remove(connection)) {
      throw new IllegalArgumentException("Connection picked not in pool: " + connection);
    }
    return connection;
  }

  private S createConnection(Amount<Long, Time> timeout) throws Exception {
    S connection = connectionFactory.create(timeout);
    if (connection != null) {
      connectionsCreated.incrementAndGet();
    }
    return connection;
  }

  private void addConnection(S connection) {
    poolLock.lock();
    try {
      availableConnections.add(connection);
      available.signal();
    } finally {
      poolLock.unlock();
    }
  }
}
