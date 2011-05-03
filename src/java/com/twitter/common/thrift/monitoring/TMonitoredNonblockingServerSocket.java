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

package com.twitter.common.thrift.monitoring;

import com.google.common.base.Preconditions;
import com.twitter.common.net.monitoring.ConnectionMonitor;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TTransportException;

import java.net.InetSocketAddress;

/**
 * Extension of TNonblockingServerSocket that allows for tracking of connected clients.
 *
 * @author William Farner
 */
public class TMonitoredNonblockingServerSocket extends TNonblockingServerSocket {
  private final ConnectionMonitor monitor;

  public TMonitoredNonblockingServerSocket(int port, ConnectionMonitor monitor)
      throws TTransportException {
    super(port);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  public TMonitoredNonblockingServerSocket(int port, int clientTimeout, ConnectionMonitor monitor)
      throws TTransportException {
    super(port, clientTimeout);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  public TMonitoredNonblockingServerSocket(InetSocketAddress bindAddr, ConnectionMonitor monitor)
      throws TTransportException {
    super(bindAddr);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  public TMonitoredNonblockingServerSocket(InetSocketAddress bindAddr, int clientTimeout,
      ConnectionMonitor monitor) throws TTransportException {
    super(bindAddr, clientTimeout);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  @Override
  protected TNonblockingSocket acceptImpl() throws TTransportException {
    /* TODO(William Farner): Finish implementing...may require an object proxy.
    final TNonblockingSocket socket = super.acceptImpl();

    TNonblockingSocket wrappedSocket = new TNonblockingSocket(socket.get) {
      @Override public void close() {
        super.close();
        monitor.disconnected(this);
      }
    };

    monitor.connected(wrappedSocket, socket.getSocket().getInetAddress());

    return wrappedSocket;

    */
    return super.acceptImpl();
  }

  @Override
  public void close() {
    super.close();
  }
}
