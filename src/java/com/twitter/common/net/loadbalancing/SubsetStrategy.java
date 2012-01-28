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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.twitter.common.base.Closure;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.net.loadbalancing.RequestTracker.RequestResult;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A load balancer that maintains a fixed upper bound on the number of backends that will be made
 * available for a wrapped load balancer.
 *
 * TODO(William Farner): May want to consider periodically swapping subsets.
 *
 * TODO(William Farner): May want to catch ResourceExhaustedExceptions from wrapped strategy and adjust
 *    subset if possible.
 *
 * @author William Farner
 */
public class SubsetStrategy<S> implements LoadBalancingStrategy<S> {
  private final LoadBalancingStrategy<S> wrapped;
  private final int maxBackends;

  private Set<S> backendSubset = Sets.newHashSet();

  public SubsetStrategy(int maxBackends, LoadBalancingStrategy<S> wrapped) {
    Preconditions.checkArgument(maxBackends > 0);
    this.maxBackends = maxBackends;
    this.wrapped = Preconditions.checkNotNull(wrapped);
  }

  @Override
  public void offerBackends(Set<S> offeredBackends, Closure<Collection<S>> onBackendsChosen) {
    List<S> allTargets = Lists.newArrayList(offeredBackends);
    Collections.shuffle(allTargets);
    backendSubset = ImmutableSet.copyOf(
        allTargets.subList(0, Math.min(maxBackends, allTargets.size())));
    wrapped.offerBackends(backendSubset, onBackendsChosen);
  }

  @Override
  public void addConnectResult(S backendKey, ConnectionResult result,
      long connectTimeNanos) {
    if (backendSubset.contains(backendKey)) {
      wrapped.addConnectResult(backendKey, result, connectTimeNanos);
    }
  }

  @Override
  public void connectionReturned(S backendKey) {
    if (backendSubset.contains(backendKey)) {
      wrapped.connectionReturned(backendKey);
    }
  }

  @Override
  public void addRequestResult(S requestKey, RequestResult result, long requestTimeNanos) {
    Preconditions.checkNotNull(requestKey);

    if (backendSubset.contains(requestKey)) {
      wrapped.addRequestResult(requestKey, result, requestTimeNanos);
    }
  }

  @Override
  public S nextBackend() throws ResourceExhaustedException {
    return wrapped.nextBackend();
  }
}
