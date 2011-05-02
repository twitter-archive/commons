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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

import com.google.common.base.Preconditions;

import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.ZooKeeperServer.BasicDataTreeBuilder;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;

import com.twitter.common.application.ActionRegistry;
import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.io.FileUtils;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.zookeeper.ZooKeeperClient;

/**
 * A helper class for starting in-process ZooKeeper server and clients.
 *
 * <p>This is ONLY meant to be used for testing.
 */
public class ZooKeeperTestServer {
  private static final Amount<Integer, Time> DEFAULT_SESSION_TIMEOUT =
      Amount.of(100, Time.MILLISECONDS);

  private final ZooKeeperServer zooKeeperServer;
  private final ActionRegistry shutdownRegistry;
  private NIOServerCnxn.Factory connectionFactory;
  private int port;

  /**
   * @param port the port to start the zoo keeper server on - {@code 0} picks an ephemeral port
   * @param shutdownRegistry a registry that will be used to register client and server shutdown
   *     commands.  It is up to the caller to execute the registered actions at an appropriate time.
   * @throws IOException if there was aproblem creating the server's database
   */
  public ZooKeeperTestServer(int port, ActionRegistry shutdownRegistry) throws IOException {
    Preconditions.checkArgument(0 <= port && port <= 0xFFFF);
    this.port = port;
    this.shutdownRegistry = Preconditions.checkNotNull(shutdownRegistry);

    zooKeeperServer =
        new ZooKeeperServer(new FileTxnSnapLog(createTempDir(), createTempDir()),
            new BasicDataTreeBuilder());
  }

  /**
   * Starts zookeeper up on the configured port.  If the configured port is the ephemeral port
   * (@{code 0}), then the actual chosen port is returned.
   */
  public final int startNetwork() throws IOException, InterruptedException {
    connectionFactory = new NIOServerCnxn.Factory(new InetSocketAddress(port));
    connectionFactory.startup(zooKeeperServer);
    shutdownRegistry.addAction(new Command() {
      @Override public void execute() {
        shutdownNetwork();
      }
    });
    port = zooKeeperServer.getClientPort();
    return port;
  }

  /**
   * Starts zookeeper back up on the last used port.
   */
  public final void restartNetwork() throws IOException, InterruptedException {
    checkEphemeralPortAssigned();
    Preconditions.checkState(!connectionFactory.isAlive());
    startNetwork();
  }

  /**
   * Shuts down the in-process zookeeper network server.
   */
  public final void shutdownNetwork() {
    if (connectionFactory.isAlive()) {
      connectionFactory.shutdown();
    }
  }

  /**
   * Expires the active session for the given client.  The client should be one returned from
   * {@link #createClient}.
   *
   * @param zkClient the client to expire
   * @throws ZooKeeperClient.ZooKeeperConnectionException if a problem is encountered connecting to
   *    the local zk server while trying to expire the session
   * @throws InterruptedException if interrupted while requesting expiration
   */
  public final void expireClientSession(ZooKeeperClient zkClient)
      throws ZooKeeperClient.ZooKeeperConnectionException, InterruptedException {
    zooKeeperServer.closeSession(zkClient.get().getSessionId());
  }

  /**
   * Returns the current port to connect to the in-process zookeeper instance.
   */
  public final int getPort() {
    checkEphemeralPortAssigned();
    return port;
  }

  /**
   * Returns a new zookeeper client connected to the in-process zookeeper server with the
   * {@link #DEFAULT_SESSION_TIMEOUT}.
   */
  public final ZooKeeperClient createClient() {
    return createClient(DEFAULT_SESSION_TIMEOUT);
  }

  /**
   * Returns a new zookeeper client connected to the in-process zookeeper server with a custom
   * {@code sessionTimeout}.
   */
  public final ZooKeeperClient createClient(Amount<Integer, Time> sessionTimeout) {
    final ZooKeeperClient client = new ZooKeeperClient(sessionTimeout, InetSocketAddress
        .createUnresolved("127.0.0.1", port));
    shutdownRegistry.addAction(new ExceptionalCommand<InterruptedException>() {
      @Override public void execute() {
        client.close();
      }
    });
    return client;
  }

  private void checkEphemeralPortAssigned() {
    Preconditions.checkState(port > 0, "startNetwork must be called first");
  }

  private File createTempDir() {
    final File tempDir = FileUtils.createTempDir();
    shutdownRegistry.addAction(new ExceptionalCommand<IOException>() {
      @Override public void execute() throws IOException {
        org.apache.commons.io.FileUtils.deleteDirectory(tempDir);
      }
    });
    return tempDir;
  }
}
