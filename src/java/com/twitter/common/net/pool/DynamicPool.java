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
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.twitter.common.base.Closure;
import com.twitter.common.net.loadbalancing.LoadBalancer;
import com.twitter.common.net.pool.DynamicHostSet.MonitorException;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * An ObjectPool that maintains a set of connections for a set of service endpoints defined by a
 * {@link com.twitter.common.zookeeper.ServerSet}.
 *
 * @param <H> The type that contains metadata information about hosts, such as liveness and address.
 * @param <T> The raw connection type that is being pooled.
 * @param <E> The type that identifies the endpoint of the pool, such as an address.
 * @author John Sirois
 */
public class DynamicPool<H, T, E> implements ObjectPool<Connection<T, E>> {

  private final MetaPool<T, E> pool;

  /**
   * Creates a new ServerSetConnectionPool and blocks on an initial read and constructions of pools
   * for the given {@code serverSet}.
   *
   * @param hostSet the dynamic set of available servers to pool connections for
   * @param endpointPoolFactory a factory that can generate a connection pool for an endpoint
   * @param loadBalancer Load balancer to manage request flow.
   * @param onBackendsChosen A callback to notify of chosen backends.
   * @param restoreInterval the interval after connection errors start occurring for a target to
   *     begin checking to see if it has come back to a healthy state
   * @param endpointExtractor Function that transforms a service instance into an endpoint instance.
   * @param livenessChecker Filter that will determine whether a host indicates itself as available.
   * @throws MonitorException if there is a problem monitoring the host set
   */
  public DynamicPool(DynamicHostSet<H> hostSet,
      Function<E, ObjectPool<Connection<T, E>>> endpointPoolFactory,
      LoadBalancer<E> loadBalancer,
      Closure<Collection<E>> onBackendsChosen,
      Amount<Long, Time> restoreInterval,
      Function<H, E> endpointExtractor,
      Predicate<H> livenessChecker)
      throws DynamicHostSet.MonitorException {
    Preconditions.checkNotNull(hostSet);
    Preconditions.checkNotNull(endpointPoolFactory);

    pool = new MetaPool<T, E>(loadBalancer, onBackendsChosen, restoreInterval);

    // TODO(John Sirois): consider an explicit start/stop
    hostSet.monitor(new PoolMonitor<H, Connection<T, E>>(endpointPoolFactory, endpointExtractor,
        livenessChecker) {
      @Override protected void onPoolRebuilt(Set<ObjectPool<Connection<T, E>>> deadPools,
          Map<E, ObjectPool<Connection<T, E>>> livePools) {
        poolRebuilt(deadPools, livePools);
      }
    });
  }

  @VisibleForTesting
  void poolRebuilt(Set<ObjectPool<Connection<T, E>>> deadPools,
      Map<E, ObjectPool<Connection<T, E>>> livePools) {

    pool.setBackends(livePools);

    for (ObjectPool<Connection<T, E>> deadTargetPool : deadPools) {
      deadTargetPool.close();
    }
  }

  @Override
  public Connection<T, E> get() throws ResourceExhaustedException, TimeoutException {
    return pool.get();
  }

  @Override
  public Connection<T, E> get(Amount<Long, Time> timeout)
      throws ResourceExhaustedException, TimeoutException {
    return pool.get(timeout);
  }

  @Override
  public void release(Connection<T, E> connection) {
    pool.release(connection);
  }

  @Override
  public void remove(Connection<T, E> connection) {
    pool.remove(connection);
  }

  @Override
  public void close() {
    pool.close();
  }

  private abstract class PoolMonitor<H, S extends Connection<?, ?>>
      implements DynamicHostSet.HostChangeMonitor<H> {

    private final Function<E, ObjectPool<S>> endpointPoolFactory;
    private final Function<H, E> endpointExtractor;
    private final Predicate<H> livenessTest;

    public PoolMonitor(Function<E, ObjectPool<S>> endpointPoolFactory,
        Function<H, E> endpointExtractor,
        Predicate<H> livenessTest) {
      this.endpointPoolFactory = endpointPoolFactory;
      this.endpointExtractor = endpointExtractor;
      this.livenessTest = livenessTest;
    }

    private final Map<E, ObjectPool<S>> endpointPools = Maps.newHashMap();

    @Override
    public synchronized void onChange(ImmutableSet<H> serverSet) {
      // TODO(John Sirois): change onChange to pass the delta data since its already computed by
      // ServerSet

      Map<E, H> newEndpoints =
          Maps.uniqueIndex(Iterables.filter(serverSet, livenessTest), endpointExtractor);

      Set<E> deadEndpoints = ImmutableSet.copyOf(
          Sets.difference(endpointPools.keySet(), newEndpoints.keySet()));
      Set<ObjectPool<S>> deadPools = Sets.newHashSet();
      for (E endpoint : deadEndpoints) {
        ObjectPool<S> deadPool = endpointPools.remove(endpoint);
        deadPools.add(deadPool);
      }

      Set<E> addedEndpoints = ImmutableSet.copyOf(
          Sets.difference(newEndpoints.keySet(), endpointPools.keySet()));
      for (E endpoint : addedEndpoints) {
        ObjectPool<S> endpointPool = endpointPoolFactory.apply(endpoint);
        endpointPools.put(endpoint, endpointPool);
      }

      onPoolRebuilt(deadPools, ImmutableMap.copyOf(endpointPools));
    }

    protected abstract void onPoolRebuilt(Set<ObjectPool<S>> deadPools,
        Map<E, ObjectPool<S>> livePools);
  }
}
