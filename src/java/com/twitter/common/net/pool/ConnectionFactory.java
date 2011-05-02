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

/**
 * A factory for connections that also dictates policy for the size of the connection population.
 *
 * <p>TODO(John Sirois): separate concerns - mixing in willCreate/null protocol is already tangling
 * implementation code
 *
 * @author John Sirois
 */
public interface ConnectionFactory<S extends Connection<?, ?>> {

  /**
   * Checks whether this factory might create a connection if requested.
   *
   * @return {@code} true if this factory might create a connection at this point in time; ie
   * a call to {@link #create} might not have returned {@code null}.  May return true to multiple
   * threads if concurrently creating connections.
   */
  boolean mightCreate();

  /**
   * Attempts to create a new connection within the given timeout and subject to this factory's
   * connection population size policy.
   *
   * @param timeout the maximum amount of time to wait
   * @return a new connection or null if there are too many connections already
   * @throws Exception if there was a problem creating the connection or establishing the connection
   *     takes too long
   */
  S create(Amount<Long, Time> timeout) throws Exception;

  /**
   * Destroys a connection.  It is an error to attempt to destroy a connection this factory did
   * not {@link #create}
   *
   * @param connection The connection to destroy.
   */
  void destroy(S connection);
}
