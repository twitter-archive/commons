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
import com.twitter.common.net.loadbalancing.LoadBalancingStrategy.ConnectionResult;

import java.util.Collection;
import java.util.Set;

/**
 * A load balancer, which will be used to determine which of a set of backends should be connected
 * to for service calls.  It is expected that the backends themselves can be changed at any time,
 * and the load balancer should immediately restrict itself to using only those backends.
 *
 * It is likely that the load balancer implementation will periodically receive information about
 * backends that it technically should no longer know about.  An example is calls to
 * {@link #requestResult(Object, RequestResult, long)} and {@link #released(Object)} for
 * in-flight requests after backends were changed by {@link #offerBackends(Set, Closure)}.
 *
 * @author William Farner
 */
public interface LoadBalancer<K> extends RequestTracker<K> {

  /**
   * Offers a set of backends that the load balancer should choose from to distribute load amongst.
   *
   * @param offeredBackends Backends to choose from.
   * @param onBackendsChosen A callback that should be notified when the offered backends have been
   *     (re)chosen from.
   */
  void offerBackends(Set<K> offeredBackends, Closure<Collection<K>> onBackendsChosen);

  /**
   * Gets the next backend that a request should be sent to.
   *
   * @return Next backend to send a request.
   * @throws ResourceExhaustedException If there are no available backends.
   */
  K nextBackend() throws ResourceExhaustedException;

  /**
   * Signals the load balancer that a connection was made.
   *
   * @param backend The backend that was connected to.
   * @param connectTimeNanos The time spent waiting for the connection to be established.
   */
  void connected(K backend, long connectTimeNanos);

  /**
   * Signals the load balancer that a connection was attempted, but failed.
   *
   * @param backend The backend to which connection attempt was made.
   * @param result The result of the connection attempt (only FAILED and TIMEOUT are permitted).
   */
  void connectFailed(K backend, ConnectionResult result);

  /**
   * Signals the load balancer that a connection was released, and is idle.
   *
   * @param connection Idle connection.
   */
  void released(K connection);
}
