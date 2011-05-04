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

import com.google.common.base.Supplier;

import java.io.Closeable;

/**
 * An interface to a connection resource that may become invalid.
 *
 * @author John Sirois
 */
public interface Connection<T, E> extends Supplier<T>, Closeable {

  /**
   * This will always be the same underlying connection for the lifetime of this object.
   *
   * @return the connection
   */
  @Override T get();

  /**
   * @return {@code true} if the supplied connection is valid for use.
   */
  boolean isValid();

  /**
   * Closes this connection.
   */
  void close();

  /**
   * @return the endpoint this connection is connected to.
   */
  E getEndpoint();
}
