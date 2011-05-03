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

import com.google.common.collect.Sets;
import com.google.common.testing.TearDown;
import com.google.common.testing.junit4.TearDownTestCase;
import com.twitter.common.net.monitoring.TrafficMonitor;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.thrift.Thrift;
import com.twitter.common.thrift.ThriftFactory;
import com.twitter.common.thrift.ThriftServer;
import com.twitter.thrift.ThriftService;
import com.twitter.thrift.ThriftService.Iface;
import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author William Farner
 */
@Ignore
public class MonitoredThriftClientServerTest extends TearDownTestCase {

  private static final int CONNECTIONS_PER_HOST = 1;

  private ThriftServer server;
  private ThriftService.Iface client;
  private TrafficMonitor<InetSocketAddress> serverMonitor;
  private TrafficMonitor<InetSocketAddress> clientMonitor;

  @Before
  public void setUp() {
    serverMonitor = new TrafficMonitor<InetSocketAddress>("test-server");
    clientMonitor = new TrafficMonitor<InetSocketAddress>("test-client");

    server = new TestService();
    server.start(new ThriftServer.ServerSetup("test", 0, new ThriftService.Processor(server),
        ThriftServer.BINARY_PROTOCOL.get(), 1, Amount.of(100, Time.MILLISECONDS), serverMonitor));
    prepareClient(server.getListeningPort());
  }

  @After
  public void shutdown() throws Exception {
    server.shutdown();
  }

  @Test
  public void testSingleRequest() throws Exception {
    client.getName();
    server.awaitShutdown(Amount.of(10L, Time.SECONDS));

    verifyConnections(serverMonitor, 0);
    verifyConnections(clientMonitor, 0);
    verifySuccessCount(serverMonitor, 1);
    verifyFailureCount(serverMonitor, 1);  // Failure for the final timed-out read.
    verifySuccessCount(clientMonitor, 1);
    verifyFailureCount(clientMonitor, 0);
    verifyLifetimeRequestCount(serverMonitor, 2);
    verifyLifetimeRequestCount(clientMonitor, 1);
  }

  @Test
  public void testMultipleRequests() throws Exception {
    client.getName();
    client.getStatus();
    server.awaitShutdown(Amount.of(10L, Time.SECONDS));

    verifyConnections(serverMonitor, 0);
    verifyConnections(clientMonitor, 0);
    verifySuccessCount(serverMonitor, 2);
    verifyFailureCount(serverMonitor, 1);  // Failure for the final timed-out read.
    verifySuccessCount(clientMonitor, 2);
    verifyFailureCount(clientMonitor, 0);
    verifyLifetimeRequestCount(serverMonitor, 3);
    verifyLifetimeRequestCount(clientMonitor, 2);
  }

  @Test
  public void testServerSideException() throws Exception {
    try {
      client.getCounters();
      fail();
    } catch (TException e) {
      // Expected.
    }

    server.awaitShutdown(Amount.of(10L, Time.SECONDS));

    verifyConnections(serverMonitor, 0);
    verifyConnections(clientMonitor, 0);
    verifySuccessCount(serverMonitor, 0);
    verifyFailureCount(serverMonitor, 1);
    verifySuccessCount(clientMonitor, 0);
    verifyFailureCount(clientMonitor, 1);
    verifyLifetimeRequestCount(serverMonitor, 1);  // Extra request for the final timed-out read.
    verifyLifetimeRequestCount(clientMonitor, 1);
  }

  private void verifyConnections(TrafficMonitor<InetSocketAddress> monitor, int connectionCount) {
    int connections = 0;
    for (TrafficMonitor<InetSocketAddress>.TrafficInfo info : monitor.getTrafficInfo().values()) {
      connections += info.getConnectionCount();
    }

    assertThat(connections, is(connectionCount));
  }

  private void verifySuccessCount(TrafficMonitor<InetSocketAddress> monitor, int requestCount) {
    int requests = 0;
    for (TrafficMonitor<InetSocketAddress>.TrafficInfo info : monitor.getTrafficInfo().values()) {
      requests += info.getRequestSuccessCount();
    }

    assertThat(requests, is(requestCount));
  }

  private void verifyFailureCount(TrafficMonitor<InetSocketAddress> monitor, int failCount) {
    int failures = 0;
    for (TrafficMonitor<InetSocketAddress>.TrafficInfo info : monitor.getTrafficInfo().values()) {
      failures += info.getRequestFailureCount();
    }

    assertThat(failures, is(failCount));
  }

  private void verifyLifetimeRequestCount(TrafficMonitor<InetSocketAddress> monitor,
      long requestCount) {
    assertThat(monitor.getLifetimeRequestCount(), is(requestCount));
  }

  private void prepareClient(int port) {
    ThriftFactory<Iface> factory = ThriftFactory.create(ThriftService.Iface.class)
        .withMaxConnectionsPerEndpoint(CONNECTIONS_PER_HOST);
    clientMonitor = factory.getMonitor();

    final Thrift<ThriftService.Iface> thrift =
        factory.build(Sets.newHashSet(new InetSocketAddress(port)));
    addTearDown(new TearDown() {
      @Override public void tearDown() {
        thrift.close();
      }
    });

    client = thrift.builder()
        .noRetries()
        .blocking()
        .create();
  }

  private class TestService extends ThriftServer {
    TestService() {
      super("Test", "1");
    }

    @Override protected void tryShutdown() throws Exception {
      // No-op.
    }

    @Override public String getStatusDetails() throws TException {
      throw new UnsupportedOperationException();
    }

    @Override public Map<String, Long> getCounters() throws TException {
      throw new TException();
    }

    @Override public long getCounter(String key) throws TException {
      throw new UnsupportedOperationException();
    }

    @Override public void setOption(String key, String value) throws TException {
      throw new UnsupportedOperationException();
    }

    @Override public String getOption(String key) throws TException {
      throw new UnsupportedOperationException();
    }

    @Override public Map<String, String> getOptions() throws TException {
      throw new UnsupportedOperationException();
    }
  }
}
