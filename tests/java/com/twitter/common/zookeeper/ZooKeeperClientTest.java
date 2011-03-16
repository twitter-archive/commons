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

package com.twitter.common.zookeeper;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.testing.junit4.JUnitAsserts.assertNotEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class ZooKeeperClientTest extends BaseZooKeeperTest {
  @Test
  public void testGet() throws Exception {
    final ZooKeeperClient zkClient = createZkClient();
    shutdownNetwork();
    try {
      zkClient.get(Amount.of(50L, Time.MILLISECONDS));
      fail("Expected client connection to timeout while network down");
    } catch (TimeoutException e) {
      // expected
    }

    final CountDownLatch blockingGetComplete = new CountDownLatch(1);
    final AtomicReference<ZooKeeper> client = new AtomicReference<ZooKeeper>();
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          client.set(zkClient.get());
        } catch (ZooKeeperConnectionException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        } finally {
          blockingGetComplete.countDown();
        }
      }
    }).start();

    restartNetwork();

    // Hung blocking connects should succeed when server connection comes up
    blockingGetComplete.await();
    assertNotNull(client.get());

    // New connections should succeed now that network is back up
    long sessionId = zkClient.get().getSessionId();

    // While connected the same client should be reused (no new connections while healthy)
    assertSame(client.get(), zkClient.get());

    shutdownNetwork();
    // Our client doesn't know the network is down yet so we should be able to get()
    ZooKeeper zooKeeper = zkClient.get();
    try {
      zooKeeper.exists("/", false);
      fail("Expected client operation to fail while network down");
    } catch (ConnectionLossException e) {
      // expected
    }

    restartNetwork();
    assertEquals("Expected connection to be re-established with existing session",
        sessionId, zkClient.get().getSessionId());
  }

  @Test
  public void testClose() throws Exception {
    ZooKeeperClient zkClient = createZkClient(Amount.of(1, Time.MINUTES));
    zkClient.close();

    // Close should be idempotent
    zkClient.close();

    long firstSessionId = zkClient.get().getSessionId();

    // Close on an open client should force session re-establishment
    zkClient.close();

    assertNotEqual(firstSessionId, zkClient.get().getSessionId());
  }
}
