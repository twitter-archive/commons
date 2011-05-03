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
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.net.loadbalancing.RequestTracker.RequestResult;

import java.util.Collection;
import java.util.Set;

/**
 * A strategy for balancing request load among backends.
 *
 * Strategies should be externally synchronized, and therefore do not have to worry about reentrant
 * access.
 *
 * @author William Farner
 */
public interface LoadBalancingStrategy<K> {

  /**
   * Offers a set of backends that the load balancer should choose from to distribute load amongst.
   *
   * @param offeredBackends Backends to choose from.
   * @param onBackendsChosen A callback that should be notified when the offered backends have been
   *     (re)chosen from.
   */
  public void offerBackends(Set<K> offeredBackends, Closure<Collection<K>> onBackendsChosen);

  /**
   * Gets the next backend that a request should be sent to.
   *
   * @return Next backend to send a request.
   * @throws ResourceExhaustedException If there are no available backends.
   */
  public K nextBackend() throws ResourceExhaustedException;

  /**
   * Offers information about a connection result.
   *
   * @param key Backend key.
   * @param result Connection result.
   * @param connectTimeNanos Time spent waiting for connection to be established.
   */
  public void addConnectResult(K key, ConnectionResult result, long connectTimeNanos);

  /**
   * Offers information about a connection that was returned.
   *
   * @param key Backend key.
   */
  public void connectionReturned(K key);

  /**
   * Offers information about a request result.
   *
   * @param key Backend key.
   * @param result Request result.
   * @param requestTimeNanos Time spent waiting for a connection to be established.
   */
  public void addRequestResult(K key, RequestResult result, long requestTimeNanos);

  enum ConnectionResult {
    FAILED,
    TIMEOUT,
    SUCCESS
  }
}
