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

import java.io.IOException;

import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.junit.Before;
import org.junit.Test;

import junit.framework.TestCase;

import com.twitter.thrift.Status;

import static org.easymock.EasyMock.createMock;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests functionality of the ThriftServer.
 *
 * @author William Farner
 */
public class ThriftServerTest extends TestCase {

  private FakeServer testServer;
  private ThriftServer.ServerSetup serverSetup;

  private static final String SERVER_NAME = "Test";
  private static final String SERVER_VERSION = "test-version";

  @Before
  public void setUp() throws IOException {
    testServer = new FakeServer();
    serverSetup = new ThriftServer.ServerSetup(0,
        createMock(TProcessor.class), null);
  }

  @Test
  public void testCorrectName() throws TException {
    assertThat(testServer.getName(), is(SERVER_NAME));
  }

  @Test
  public void testCorrectVersion() throws TException {
    assertThat(testServer.getVersion(), is(SERVER_VERSION));
  }

  @Test
  public void testStartingStatus() throws TException {
    assertThat(testServer.getStatus(), is(Status.STARTING));
  }

  @Test
  public void testStart() throws TException {
    testServer.start(serverSetup);
    assertThat(testServer.getStatus(), is(Status.ALIVE));
  }

  @Test
  public void testFailedStart() throws TException {
    testServer.failStart = true;
    testServer.start(serverSetup);
    assertThat(testServer.getStatus(), is(Status.DEAD));
  }

  @Test
  public void testShutdown() throws TException {
    testServer.start(serverSetup);
    testServer.shutdown();
    assertThat(testServer.getStatus(), is(Status.STOPPED));
  }

  @Test
  public void testVetoedShutdown() throws TException {
    testServer.start(serverSetup);
    testServer.vetoShutdown = true;
    testServer.shutdown();
    assertThat(testServer.getStatus(), is(Status.WARNING));
  }

  @Test
  public void testImmediateShutdown() throws TException {
    testServer.shutdown();
    assertThat(testServer.getStatus(), is(Status.STOPPED));
  }

  @Test
  public void testGetServerSocketFor() throws TTransportException {
    TNonblockingServerSocket ephemeralThriftSocket = new TNonblockingServerSocket(0);
    try {
      assertTrue(ThriftServer.getServerSocketFor(ephemeralThriftSocket).getLocalPort() > 0);
    } finally {
      ephemeralThriftSocket.close();
    }
  }

  private class FakeServer extends ThriftServer {
    public boolean failStart = false;
    public boolean vetoShutdown = false;

    public FakeServer() {
      super(SERVER_NAME, SERVER_VERSION);
    }

    @Override
    protected void doStart(ServerSetup setup) throws TTransportException {
      if (failStart) throw new TTransportException("Injected failure.");
    }

    @Override
    public void tryShutdown() throws Exception {
      if (vetoShutdown) throw new Exception("Injected failure.");
      assertThat(testServer.getStatus(), is(Status.STOPPING));
    }
  }
}
