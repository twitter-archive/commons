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
import com.google.common.collect.Maps;
import com.twitter.common.net.monitoring.ConnectionMonitor;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.Map;

/**
 * Extension of TServerSocket that allows for tracking of connected clients.
 *
 * @author William Farner
 */
public class TMonitoredServerSocket extends TServerSocket {
  private ConnectionMonitor<InetSocketAddress> monitor;

  public TMonitoredServerSocket(ServerSocket serverSocket,
      ConnectionMonitor<InetSocketAddress> monitor) {
    super(serverSocket);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  public TMonitoredServerSocket(ServerSocket serverSocket, int clientTimeout,
      ConnectionMonitor<InetSocketAddress> monitor) {
    super(serverSocket, clientTimeout);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  public TMonitoredServerSocket(int port, ConnectionMonitor<InetSocketAddress> monitor)
      throws TTransportException {
    super(port);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  public TMonitoredServerSocket(int port, int clientTimeout,
      ConnectionMonitor<InetSocketAddress> monitor) throws TTransportException {
    super(port, clientTimeout);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  public TMonitoredServerSocket(InetSocketAddress bindAddr,
      ConnectionMonitor<InetSocketAddress> monitor) throws TTransportException {
    super(bindAddr);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  public TMonitoredServerSocket(InetSocketAddress bindAddr, int clientTimeout,
      ConnectionMonitor<InetSocketAddress> monitor) throws TTransportException {
    super(bindAddr, clientTimeout);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  private final Map<TSocket, InetSocketAddress> addressMap =
      Collections.synchronizedMap(Maps.<TSocket, InetSocketAddress>newHashMap());

  public InetSocketAddress getAddress(TSocket socket) {
    return addressMap.get(socket);
  }

  @Override
  protected TSocket acceptImpl() throws TTransportException {
    final TSocket socket = super.acceptImpl();
    final InetSocketAddress remoteAddress =
        (InetSocketAddress) socket.getSocket().getRemoteSocketAddress();

    TSocket monitoredSocket = new TSocket(socket.getSocket()) {
      boolean closed = false;

      @Override public void close() {
        try {
          super.close();
        } finally {
          if (!closed) {
            monitor.released(remoteAddress);
            addressMap.remove(this);
          }
          closed = true;
        }
      }
    };

    addressMap.put(monitoredSocket, remoteAddress);

    monitor.connected(remoteAddress);
    return monitoredSocket;
  }

  @Override
  public void close() {
    super.close();
  }
}
