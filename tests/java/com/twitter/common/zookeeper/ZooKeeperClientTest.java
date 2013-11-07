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

package com.twitter.common.zookeeper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.KeeperException.NoAuthException;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.zookeeper.ZooKeeperClient.Credentials;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;

import static com.google.common.testing.junit4.JUnitAsserts.assertNotEqual;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class ZooKeeperClientTest extends BaseZooKeeperTest {

  public ZooKeeperClientTest() {
    super(Amount.of(1, Time.DAYS));
  }

  @Test
  public void testGet() throws Exception {
    final ZooKeeperClient zkClient = createZkClient();
    shutdownNetwork();
    try {
      zkClient.get(Amount.of(50L, Time.MILLISECONDS));
      fail("Expected client connection to timeout while network down");
    } catch (TimeoutException e) {
      assertTrue(zkClient.isClosed());
    }
    assertNull(zkClient.getZooKeeperClientForTests());

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

  /**
   * Test that if a blocking get() call gets interrupted, after a connection has been created
   * but before it's connected, the zk connection gets closed.
   */
  @Test
  public void testGetInterrupted() throws Exception {
    final ZooKeeperClient zkClient = createZkClient();
    shutdownNetwork();

    final CountDownLatch blockingGetComplete = new CountDownLatch(1);
    final AtomicBoolean interrupted = new AtomicBoolean();
    final AtomicReference<ZooKeeper> client = new AtomicReference<ZooKeeper>();
    Thread getThread = new Thread(new Runnable() {
      @Override public void run() {
        try {
          client.set(zkClient.get());
        } catch (ZooKeeperConnectionException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
          interrupted.set(true);
          throw new RuntimeException(e);
        } finally {
          blockingGetComplete.countDown();
        }
      }
    });
    getThread.start();

    while (zkClient.getZooKeeperClientForTests() == null) {
      Thread.sleep(100);
    }

    getThread.interrupt();
    blockingGetComplete.await();

    assertNull("The zk connection should have been closed", zkClient.getZooKeeperClientForTests());
    assertTrue("The waiter thread should have been interrupted", interrupted.get());
    assertTrue(zkClient.isClosed());
  }

  @Test
  public void testClose() throws Exception {
    ZooKeeperClient zkClient = createZkClient();
    zkClient.close();

    // Close should be idempotent
    zkClient.close();

    long firstSessionId = zkClient.get().getSessionId();

    // Close on an open client should force session re-establishment
    zkClient.close();

    assertNotEqual(firstSessionId, zkClient.get().getSessionId());
  }

  @Test
  public void testCredentials() throws Exception {
    String path = "/test";
    ZooKeeperClient authenticatedClient = createZkClient("creator", "creator");
    assertEquals(path,
        authenticatedClient.get().create(path, "42".getBytes(),
            ZooKeeperUtils.EVERYONE_READ_CREATOR_ALL, CreateMode.PERSISTENT));

    ZooKeeperClient unauthenticatedClient = createZkClient(Credentials.NONE);
    assertEquals("42", getData(unauthenticatedClient, path));
    try {
      setData(unauthenticatedClient, path, "37");
      fail("Expected unauthenticated write attempt to fail");
    } catch (NoAuthException e) {
      assertEquals("42", getData(unauthenticatedClient, path));
    }

    ZooKeeperClient nonOwnerClient = createZkClient("nonowner", "nonowner");
    assertEquals("42", getData(nonOwnerClient, path));
    try {
      setData(nonOwnerClient, path, "37");
      fail("Expected non owner write attempt to fail");
    } catch (NoAuthException e) {
      assertEquals("42", getData(nonOwnerClient, path));
    }

    ZooKeeperClient authenticatedClient2 = createZkClient("creator", "creator");
    setData(authenticatedClient2, path, "37");
    assertEquals("37", getData(authenticatedClient2, path));
  }

  @Test
  public void testHasCredentials() {
    assertFalse(createZkClient().hasCredentials());
    assertFalse(createZkClient(Credentials.NONE).hasCredentials());
    assertFalse(createZkClient(new Credentials() {
      @Override
      public void authenticate(ZooKeeper zooKeeper) {
        // noop
      }
      @Override public String scheme() {
        return "";
      }
      @Override public byte[] authToken() {
        return new byte[0];
      }
    }).hasCredentials());

    assertTrue(createZkClient("creator", "creator").hasCredentials());
    assertTrue(createZkClient(new Credentials() {
      @Override public void authenticate(ZooKeeper zooKeeper) {
        // noop
      }
      @Override public String scheme() {
        return "custom";
      }
      @Override public byte[] authToken() {
        // a zero-length token should be ok - ZooKeeper says nothing about the validity of token
        // data a scheme can accept.
        return new byte[0];
      }
    }).hasCredentials());
  }

  @Test
  public void testChrootPath() throws Exception {
    ZooKeeperClient rootClient = createZkClient();
    String rootPath = "/test";
    String subPath = "/test/subtest";
    assertEquals(rootPath,
            rootClient.get().create(rootPath, "42".getBytes(),
                ZooKeeperUtils.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));
    assertEquals(subPath,
            rootClient.get().create(subPath, "37".getBytes(),
                ZooKeeperUtils.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));

    ZooKeeperClient chrootedClient = createZkClient(rootPath);
    assertArrayEquals("37".getBytes(), chrootedClient.get().getData("/subtest", false, null));
  }

  private void setData(ZooKeeperClient zkClient, String path, String data) throws Exception {
    zkClient.get().setData(path, data.getBytes(), ZooKeeperUtils.ANY_VERSION);
  }

  private String getData(ZooKeeperClient zkClient, String path) throws Exception {
    return new String(zkClient.get().getData(path, false, null));
  }
}
