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

import com.twitter.common.base.Closure;
import com.twitter.common.net.loadbalancing.RequestTracker.RequestResult;

import java.util.Collection;
import java.util.Set;

/**
 * A baseclass for LoadBalancingStrategies that use a static set of backends they are
 * {@link #offerBackends(java.util.Set, com.twitter.common.base.Closure) offered}.  Also acts as an
 * adapter, providing no-op implementations of all other LoadBalancingStrategy methods that only
 * need be overridden as required by subclass features.
 *
 * @author John Sirois
 */
abstract class StaticLoadBalancingStrategy<K> implements LoadBalancingStrategy<K> {

  @Override
  public final void offerBackends(Set<K> offeredBackends, Closure<Collection<K>> onBackendsChosen) {
    onBackendsChosen.execute(onBackendsOffered(offeredBackends));
  }

  /**
   * Subclasses must override and return a collection of the backends actually chosen for use until
   * the next offer round.
   *
   * @param offeredBackends The backends offered in a {@link
   *     #offerBackends(java.util.Set, com.twitter.common.base.Closure)} event.
   * @return The collection of backends that will be used until the next offer event.
   */
  protected abstract Collection<K> onBackendsOffered(Set<K> offeredBackends);

  @Override
  public void addConnectResult(K backendKey, ConnectionResult result, long connectTimeNanos) {
    // No-op.
  }

  @Override
  public void connectionReturned(K backendKey) {
    // No-op.
  }

  @Override
  public void addRequestResult(K requestKey, RequestResult result, long requestTimeNanos) {
    // No-op.
  }
}
