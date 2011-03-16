// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.zookeeper.testing;

import com.google.common.base.Preconditions;
import com.google.common.testing.TearDown;
import com.google.common.testing.junit4.TearDownTestCase;
import com.twitter.common.io.FileUtils;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.zookeeper.ZooKeeperClient;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;
import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.ZooKeeperServer.BasicDataTreeBuilder;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * A baseclass for in-process zookeeper tests.  Ensures
 *
 * @author John Sirois
 */
public abstract class BaseZooKeeperTest extends TearDownTestCase {
  private static final Amount<Integer, Time> DEFAULT_SESSION_TIMEOUT =
      Amount.of(100, Time.MILLISECONDS);

  private ZooKeeperServer zooKeeperServer;
  private NIOServerCnxn.Factory connectionFactory;
  private int zkPort;

  @Before
  public final void setUp() throws Exception {
    zooKeeperServer =
        new ZooKeeperServer(new FileTxnSnapLog(createTempDir(), createTempDir()),
            new BasicDataTreeBuilder());

    startupNetwork(0);
    addTearDown(new TearDown() {
      @Override public void tearDown() {
        shutdownNetwork();
      }
    });

    zkPort = zooKeeperServer.getClientPort();
  }

  private void startupNetwork(int port) throws Exception {
    connectionFactory = new NIOServerCnxn.Factory(new InetSocketAddress(port));
    connectionFactory.startup(zooKeeperServer);
    zkPort = zooKeeperServer.getClientPort();
  }

  /**
   * Starts zookeeper back up on the last used port.
   */
  protected final void restartNetwork() throws Exception {
    Preconditions.checkState(zkPort > 0);
    startupNetwork(zkPort);
  }

  /**
   * Shuts down the in-process zookeeper network server.
   */
  protected final void shutdownNetwork() {
    if (connectionFactory.isAlive()) {
      connectionFactory.shutdown();
    }
  }

  protected final void expireSession(ZooKeeperClient zkClient)
      throws ZooKeeperConnectionException, InterruptedException {
    zooKeeperServer.closeSession(zkClient.get().getSessionId());
  }

  private File createTempDir() {
    final File tempDir = FileUtils.createTempDir();
    addTearDown(new TearDown() {
      @Override public void tearDown() throws IOException {
        org.apache.commons.io.FileUtils.deleteDirectory(tempDir);
      }
    });
    return tempDir;
  }

  /**
   * Returns the current port to connect to the in-process zookeeper instance.
   */
  protected final int getPort() {
    return zkPort;
  }

  /**
   * Returns a new zookeeper client connected to the in-process zookeeper server with the
   * {@link #DEFAULT_SESSION_TIMEOUT}.
   */
  protected final ZooKeeperClient createZkClient() throws IOException {
    return createZkClient(DEFAULT_SESSION_TIMEOUT);
  }

  /**
   * Returns a new zookeeper client connected to the in-process zookeeper server with a custom
   * {@code sessionTimeout}.
   */
  protected final ZooKeeperClient createZkClient(Amount<Integer, Time> sessionTimeout)
      throws IOException {

    final ZooKeeperClient client = new ZooKeeperClient(sessionTimeout,
        InetSocketAddress.createUnresolved("127.0.0.1", zkPort));
    addTearDown(new TearDown() {
      @Override public void tearDown() throws InterruptedException {
        client.close();
      }
    });
    return client;
  }
}
