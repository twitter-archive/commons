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
import com.twitter.common.net.loadbalancing.RequestTracker;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;

import java.net.InetSocketAddress;

import static com.twitter.common.net.loadbalancing.RequestTracker.RequestResult.*;

/**
 * A TProcessor that joins a wrapped TProcessor with a monitor.
 *
 * @author William Farner
 */
public class TMonitoredProcessor implements TProcessor {
  private final TProcessor wrapped;
  private final TMonitoredServerSocket monitoredServerSocket;
  private final RequestTracker<InetSocketAddress> monitor;

  public TMonitoredProcessor(TProcessor wrapped, TMonitoredServerSocket monitoredServerSocket,
      RequestTracker<InetSocketAddress> monitor) {
    this.wrapped = Preconditions.checkNotNull(wrapped);
    this.monitoredServerSocket = Preconditions.checkNotNull(monitoredServerSocket);
    this.monitor = Preconditions.checkNotNull(monitor);
  }

  @Override
  public boolean process(TProtocol in, TProtocol out) throws TException {
    long startNanos = System.nanoTime();
    boolean exceptionThrown = false;
    try {
      return wrapped.process(in, out);
    } catch (TException e) {
      exceptionThrown = true;
      throw e;
    } finally {
      InetSocketAddress address = monitoredServerSocket.getAddress((TSocket) in.getTransport());
      Preconditions.checkState(address != null,
          "Address unknown for transport " + in.getTransport());

      monitor.requestResult(address, exceptionThrown ? FAILED : SUCCESS,
          System.nanoTime() - startNanos);
    }
  }
}
