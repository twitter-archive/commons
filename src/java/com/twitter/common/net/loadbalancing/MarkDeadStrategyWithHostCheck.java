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

import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

import com.twitter.common.util.BackoffDecider;

/**
 * A load balancing strategy that extends the functionality of the mark dead strategy by
 * integrating a hostChecker that allows hosts to transition out of a dead state
 * if the most recent connection to the host was successful.
 *
 * @param <S> typically socket address of a backend host.
 * @author Krishna Gade
 */
public class MarkDeadStrategyWithHostCheck<S> extends MarkDeadStrategy<S> {

  /**
   * LiveHostChecker implements Filter to determine whether a host is alive based on the
   * result of the most recent connection attempt to that host. It keeps a map of
   * backend -> last connection result, which gets updated every time someone tries to
   * add to connection result.
   */
  protected static class LiveHostChecker<S> implements Predicate<S> {
    private final Map<S, ConnectionResult> lastConnectionResult = Maps.newHashMap();

    /**
     * Adds the connection result of this backend to the last connection result map.
     *
     * @param backend typically the socket address of the backend.
     * @param result result of what happened when the client tried to connect to this backend.
     */
    public void addConnectResult(S backend, ConnectionResult result) {
      lastConnectionResult.put(backend, result);
    }

    /**
     * Checks if the last connection result for this backend and returns {@code true} if it
     * was {@link LoadBalancingStrategy.ConnectionResult#SUCCESS} otherwise returns {@code false}.
     *
     * @param backend typically the socket address of the backend.
     */
    @Override public boolean apply(S backend) {
      ConnectionResult result = lastConnectionResult.get(backend);
      return result != null && result == ConnectionResult.SUCCESS;
    }
  }

  // Reference to the host checker we pass to the super class.
  // We keep it here to avoid casting on every access to it.
  protected final LiveHostChecker<S> liveHostChecker;

  /**
   * Creates a mark dead strategy with the given wrapped strategy and backoff decider factory.
   * It uses a hostChecker {@link Predicate} that allows hosts to transition out
   * of a dead state if the most recent connection to the host was successful.
   *
   * @param wrappedStrategy one of the implementations of the load balancing strategy.
   * @param backoffFactory backoff decider factory per host.
   */
  public MarkDeadStrategyWithHostCheck(LoadBalancingStrategy<S> wrappedStrategy,
      Function<S, BackoffDecider> backoffFactory) {
    super(wrappedStrategy, backoffFactory, new LiveHostChecker<S>());
    // Casting to LiveHostChecker is safe here as that's the only predicate that we pass to super.
    this.liveHostChecker = ((LiveHostChecker<S>) hostChecker);
  }


  /**
   * Overrides the base class implementation by adding this connection result to the
   * host checker.
   *
   * @param backendKey typically the socket address of the backend.
   * @param result result of what happened when the client tried to connect to this backend.
   * @param connectTimeNanos time took to connect to the backend in nano seconds.
   */
  @Override
  public void addConnectResult(S backendKey, ConnectionResult result, long connectTimeNanos) {
    liveHostChecker.addConnectResult(backendKey, result);
    super.addConnectResult(backendKey, result, connectTimeNanos);
  }
}
