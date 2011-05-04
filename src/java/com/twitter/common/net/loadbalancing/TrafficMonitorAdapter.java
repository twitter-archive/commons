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

import com.google.common.base.Preconditions;
import com.twitter.common.base.Closure;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.net.monitoring.TrafficMonitor;

import java.util.Collection;
import java.util.Set;

/**
 * @author William Farner
 */
public class TrafficMonitorAdapter<K> implements LoadBalancingStrategy<K> {
  private final LoadBalancingStrategy<K> strategy;
  private final TrafficMonitor<K> monitor;

  public TrafficMonitorAdapter(LoadBalancingStrategy<K> strategy, TrafficMonitor<K> monitor) {
    this.strategy = Preconditions.checkNotNull(strategy);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  public static <K> TrafficMonitorAdapter<K> create(LoadBalancingStrategy<K> strategy,
      TrafficMonitor<K> monitor) {
    return new TrafficMonitorAdapter<K>(strategy, monitor);
  }

  @Override
  public void offerBackends(Set<K> offeredBackends, Closure<Collection<K>> onBackendsChosen) {
    strategy.offerBackends(offeredBackends, onBackendsChosen);
  }

  @Override
  public K nextBackend() throws ResourceExhaustedException {
    return strategy.nextBackend();
  }

  @Override
  public void addConnectResult(K key, ConnectionResult result, long connectTimeNanos) {
    strategy.addConnectResult(key, result, connectTimeNanos);
    if (result == ConnectionResult.SUCCESS) monitor.connected(key);
  }

  @Override
  public void connectionReturned(K key) {
    strategy.connectionReturned(key);
    monitor.released(key);
  }

  @Override
  public void addRequestResult(K key, RequestTracker.RequestResult result, long requestTimeNanos) {
    strategy.addRequestResult(key, result, requestTimeNanos);
    monitor.requestResult(key, result, requestTimeNanos);
  }
}
