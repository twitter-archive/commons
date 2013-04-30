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

package com.twitter.common.net.loadbalancing;

import java.util.Collection;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import com.twitter.common.base.Closure;
import com.twitter.common.net.loadbalancing.LoadBalancingStrategy.ConnectionResult;
import com.twitter.common.net.pool.ResourceExhaustedException;

/**
 * Implementation of a load balancer, that uses a pluggable {@link LoadBalancingStrategy} to define
 * actual load balancing behavior.  This class handles the responsibility of associating connections
 * with backends.
 *
 * Calls to {@link #connected(Object, long)},
 * {@link #requestResult(Object, RequestResult, long)}, and {@link #released(Object)} will not
 * be forwarded for unknown backends/connections.
 *
 * @author William Farner
 */
public class LoadBalancerImpl<K> implements LoadBalancer<K> {

  private final LoadBalancingStrategy<K> strategy;

  private Set<K> offeredBackends = ImmutableSet.of();

  /**
   * Creates a new load balancer that will use the given strategy.
   *
   * @param strategy Strategy to delegate load balancing work to.
   */
  public LoadBalancerImpl(LoadBalancingStrategy<K> strategy) {
    this.strategy = Preconditions.checkNotNull(strategy);
  }

  @Override
  public synchronized void offerBackends(Set<K> offeredBackends,
      final Closure<Collection<K>> onBackendsChosen) {
    this.offeredBackends = ImmutableSet.copyOf(offeredBackends);
    strategy.offerBackends(offeredBackends, new Closure<Collection<K>>() {
      @Override public void execute(Collection<K> chosenBackends) {
        onBackendsChosen.execute(chosenBackends);
      }
    });
  }

  @Override
  public synchronized K nextBackend() throws ResourceExhaustedException {
    return strategy.nextBackend();
  }

  @Override
  public synchronized void connected(K backend, long connectTimeNanos) {
    Preconditions.checkNotNull(backend);

    if (!hasBackend(backend)) return;

    strategy.addConnectResult(backend, ConnectionResult.SUCCESS, connectTimeNanos);
  }

  private boolean hasBackend(K backend) {
    return offeredBackends.contains(backend);
  }

  @Override
  public synchronized void connectFailed(K backend, ConnectionResult result) {
    Preconditions.checkNotNull(backend);
    Preconditions.checkNotNull(result);
    Preconditions.checkArgument(result != ConnectionResult.SUCCESS);

    if (!hasBackend(backend)) return;

    strategy.addConnectResult(backend, result, 0);
  }

  @Override
  public synchronized void released(K backend) {
    Preconditions.checkNotNull(backend);

    if (!hasBackend(backend)) return;

    strategy.connectionReturned(backend);
  }

  @Override
  public synchronized void requestResult(K backend, RequestResult result, long requestTimeNanos) {
    Preconditions.checkNotNull(backend);
    Preconditions.checkNotNull(result);

    if (!hasBackend(backend)) return;

    strategy.addRequestResult(backend, result, requestTimeNanos);
  }

  /**
   * Convenience method to create a new load balancer.
   *
   * @param strategy Strategy to use.
   * @param <K> Backend type.
   * @return A new load balancer.
   */
  public static <K> LoadBalancerImpl<K>
      create(LoadBalancingStrategy<K> strategy) {
    return new LoadBalancerImpl<K>(strategy);
  }
}
