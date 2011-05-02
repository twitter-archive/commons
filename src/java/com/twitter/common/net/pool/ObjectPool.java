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

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

import java.util.concurrent.TimeoutException;

/**
 * A generic object pool that provides object of a given type for exclusive use by the caller.
 * Object pools generally pool expensive resources and so offer a {@link #close} method that should
 * be used to free these resources when the pool is no longer needed.
 *
 * @author John Sirois
 */
public interface ObjectPool<T> {

  /**
   * Gets a resource potentially blocking for as long as it takes to either create a new one or wait
   * for one to be {@link #release(Object) released}.  Callers must {@link #release(Object) release}
   * the connection when they are done with it.
   *
   * @return a resource for exclusive use by the caller
   * @throws ResourceExhaustedException if no resource could be obtained because this pool was
   *     exhausted
   * @throws TimeoutException if we timed out while trying to fetch a resource
   */
  T get() throws ResourceExhaustedException, TimeoutException;

  /**
   * A convenience constant representing a no timeout.
   */
  Amount<Long,Time> NO_TIMEOUT = Amount.of(0L, Time.MILLISECONDS);

  /**
   * Gets a resource; timing out if there are none available and it takes longer than specified to
   * create a new one or wait for one to be {@link #release(Object) released}.  Callers must
   * {@link #release (Object) release} the connection when they are done with it.
   *
   * @param timeout the maximum amount of time to wait
   * @return a resource for exclusive use by the caller
   * @throws TimeoutException if the specified timeout was reached before a resource became
   *     available
   * @throws ResourceExhaustedException if no resource could be obtained because this pool was
   *     exhausted
   */
  T get(Amount<Long, Time> timeout) throws ResourceExhaustedException, TimeoutException;

  /**
   * Releases a resource obtained from this pool back into the pool of available resources. It is an
   * error to release a resource not obtained from this pool.
   *
   * @param resource Resource to release.
   */
  void release(T resource);

  /**
   * Removes a resource obtained from this pool from its available resources.  It is an error to
   * remove a resource not obtained from this pool.
   *
   * @param resource Resource to remove.
   */
  void remove(T resource);

  /**
   * Disallows further gets from this pool, "closes" all idle objects and any outstanding objects
   * when they are released.
   */
  void close();
}
