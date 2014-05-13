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

package com.twitter.common.zookeeper.testing;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.common.testing.TearDown;
import com.google.common.testing.junit4.TearDownTestCase;

import org.junit.Before;

import com.twitter.common.application.ShutdownRegistry.ShutdownRegistryImpl;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.zookeeper.ZooKeeperClient;
import com.twitter.common.zookeeper.ZooKeeperClient.Credentials;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;

/**
 * A baseclass for in-process zookeeper tests.
 * Uses ZooKeeperTestHelper to start the server and create clients: new tests should directly use
 * that helper class instead of extending this class.
 */
public abstract class BaseZooKeeperTest extends TearDownTestCase {

  private final Amount<Integer, Time> defaultSessionTimeout;
  private ZooKeeperTestServer zkTestServer;

  /**
   * Creates a test case where the test server uses its
   * {@link ZooKeeperTestServer#DEFAULT_SESSION_TIMEOUT} for clients created without an explicit
   * session timeout.
   */
  public BaseZooKeeperTest() {
    this(ZooKeeperTestServer.DEFAULT_SESSION_TIMEOUT);
  }

  /**
   * Creates a test case where the test server uses the given {@code defaultSessionTimeout} for
   * clients created without an explicit session timeout.
   */
  public BaseZooKeeperTest(Amount<Integer, Time> defaultSessionTimeout) {
    this.defaultSessionTimeout = Preconditions.checkNotNull(defaultSessionTimeout);
  }

  @Before
  public final void setUp() throws Exception {
    final ShutdownRegistryImpl shutdownRegistry = new ShutdownRegistryImpl();
    addTearDown(new TearDown() {
      @Override public void tearDown() {
        shutdownRegistry.execute();
      }
    });
    zkTestServer = new ZooKeeperTestServer(0, shutdownRegistry, defaultSessionTimeout);
    zkTestServer.startNetwork();
  }

  /**
   * Starts zookeeper back up on the last used port.
   */
  protected final void restartNetwork() throws IOException, InterruptedException {
    zkTestServer.restartNetwork();
  }

  /**
   * Shuts down the in-process zookeeper network server.
   */
  protected final void shutdownNetwork() {
    zkTestServer.shutdownNetwork();
  }

  /**
   * Expires the active session for the given client.  The client should be one returned from
   * {@link #createZkClient}.
   *
   * @param zkClient the client to expire
   * @throws ZooKeeperClient.ZooKeeperConnectionException if a problem is encountered connecting to
   *    the local zk server while trying to expire the session
   * @throws InterruptedException if interrupted while requesting expiration
   */
  protected final void expireSession(ZooKeeperClient zkClient)
      throws ZooKeeperConnectionException, InterruptedException {
    zkTestServer.expireClientSession(zkClient);
  }

  /**
   * Returns the current port to connect to the in-process zookeeper instance.
   */
  protected final int getPort() {
    return zkTestServer.getPort();
  }

  /**
   * Returns a new unauthenticated zookeeper client connected to the in-process zookeeper server
   * with the default session timeout.
   */
  protected final ZooKeeperClient createZkClient() {
    return zkTestServer.createClient();
  }

  /**
   * Returns a new authenticated zookeeper client connected to the in-process zookeeper server with
   * the default session timeout.
   */
  protected final ZooKeeperClient createZkClient(Credentials credentials) {
    return zkTestServer.createClient(credentials);
  }

  /**
   * Returns a new authenticated zookeeper client connected to the in-process zookeeper server with
   * the default session timeout.  The client is authenticated in the digest authentication scheme
   * with the given {@code username} and {@code password}.
   */
  protected final ZooKeeperClient createZkClient(String username, String password) {
    return createZkClient(ZooKeeperClient.digestCredentials(username, password));
  }

  /**
   * Returns a new unauthenticated zookeeper client connected to the in-process zookeeper server
   * with a custom {@code sessionTimeout}.
   */
  protected final ZooKeeperClient createZkClient(Amount<Integer, Time> sessionTimeout) {
    return zkTestServer.createClient(sessionTimeout);
  }

  /**
   * Returns a new authenticated zookeeper client connected to the in-process zookeeper server with
   * a custom {@code sessionTimeout}.
   */
  protected final ZooKeeperClient createZkClient(Amount<Integer, Time> sessionTimeout,
      Credentials credentials) {
    return zkTestServer.createClient(sessionTimeout, credentials);
  }

  /**
   * Returns a new authenticated zookeeper client connected to the in-process zookeeper server with
   * the default session timeout and the custom chroot path.
   */
  protected final ZooKeeperClient createZkClient(String chrootPath) {
    return zkTestServer.createClient(chrootPath);
  }
}
