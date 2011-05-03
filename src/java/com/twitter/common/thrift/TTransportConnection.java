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

package com.twitter.common.thrift;

import com.google.common.base.Preconditions;
import com.twitter.common.net.pool.Connection;
import com.twitter.common.net.pool.ConnectionPool;
import org.apache.thrift.transport.TTransport;

import java.net.InetSocketAddress;

/**
 * A {@link ConnectionPool} compatible thrift connection that can work with any valid thrift
 * transport.
 *
 * @author John Sirois
 */
public class TTransportConnection implements Connection<TTransport, InetSocketAddress> {

  private final TTransport transport;
  private final InetSocketAddress endpoint;

  public TTransportConnection(TTransport transport, InetSocketAddress endpoint) {
    this.transport = Preconditions.checkNotNull(transport);
    this.endpoint = Preconditions.checkNotNull(endpoint);
  }

  /**
   * Returns {@code true} if the underlying transport is still open.  To invalidate a transport it
   * should be closed.
   *
   * <p>TODO(John Sirois): it seems like an improper soc to have validity testing here and not also an
   * invalidation method - correct or accept
   */
  @Override
  public boolean isValid() {
    return transport.isOpen();
  }

  @Override
  public TTransport get() {
    return transport;
  }

  @Override
  public void close() {
    transport.close();
  }

  @Override
  public InetSocketAddress getEndpoint() {
    return endpoint;
  }

  @Override
  public String toString() {
    return endpoint.toString();
  }
}
