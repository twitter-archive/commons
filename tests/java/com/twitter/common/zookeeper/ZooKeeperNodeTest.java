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

import com.google.common.base.Function;
import com.twitter.common.base.Closure;
import com.twitter.common.base.Closures;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;


import static org.junit.Assert.assertEquals;

/**
 * @author Adam Samet
 */
public class ZooKeeperNodeTest extends BaseZooKeeperTest {

  private static final List<ACL> ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;
  private static final Function<byte[], String> BYTES_TO_STRING =
      new Function<byte[], String>() {
        @Override
        public String apply(byte[] from) {
          return new String(from);
        }};

  private ZooKeeperClient zkClient;

  private static class Listener<T> implements Closure<T> {
    // We use AtomicReference as a wrapper since LinkedBlockingQueue does not allow null values.
    private final BlockingQueue<AtomicReference<T>> queue =
        new LinkedBlockingQueue<AtomicReference<T>>();

    public void execute(T item) {
      queue.offer(new AtomicReference<T>(item));
    }

    public T waitForUpdate() throws InterruptedException {
      return queue.take().get();
    }
  }

  private String nodePath;

  @Before
  public void mySetUp() throws Exception {
    zkClient = createZkClient();
    ZooKeeperUtils.ensurePath(zkClient, ACL, "/twitter");
    nodePath = "/twitter/node";
  }

  @Test
  public void testZooKeeperUnavailableAtConstruction() throws Exception {
    shutdownNetwork();  // Make zk unavailable.

    // Should be fine.
    makeUninitializedNode(nodePath, Closures.<String>noop());
  }

  @Test(expected = KeeperException.class)
  public void testZooKeeperUnavailableAtInit() throws Exception {
    ZooKeeperNode zkNode = makeUninitializedNode(nodePath, Closures.<String>noop());

    shutdownNetwork();  // Make zk unavailable.

    zkNode.init();
  }

  @Test
  public void testInitialization() throws Exception {
    String data = "abcdefg";
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    ZooKeeperNode zkNode = makeUninitializedNode(nodePath, Closures.<String>noop());

    // get() should return null before initialization
    assertEquals(null, zkNode.get());

    zkNode.init();

    // Now that init has been called, the data should be synchronously reflected.
    assertEquals(data, zkNode.get());
  }

  @Test
  public void testInitialEmptyNode() throws Exception {
    Listener<String> listener = new Listener<String>();
    ZooKeeperNode<String> zkNode = makeUninitializedNode(nodePath, listener);

    assertEquals(null, zkNode.get());
    zkNode.init();
    assertEquals(null, listener.waitForUpdate());

    String data = "abcdefg";
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    assertEquals(data, listener.waitForUpdate());
  }

  @Test
  public void testChangingData() throws Exception {
    String data1 = "test_data";
    zkClient.get().create(nodePath, data1.getBytes(), ACL, CreateMode.PERSISTENT);
    Listener<String> listener = new Listener<String>();
    ZooKeeperNode<String> zkNode = makeNode(nodePath, listener);

    assertEquals(data1, listener.waitForUpdate());
    String data2 = "BLAH";
    zkClient.get().setData(nodePath, data2.getBytes(), -1);
    assertEquals(data2, listener.waitForUpdate());
  }

  @Test
  public void testRemoveNode() throws Exception {
    String data = "testdata";
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    Listener<String> listener = new Listener<String>();
    ZooKeeperNode<String> zkNode = makeNode(nodePath, listener);

    assertEquals(data, listener.waitForUpdate());

    zkClient.get().delete(nodePath, -1);
    assertEquals(null, listener.waitForUpdate());

    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    assertEquals(data, listener.waitForUpdate());
  }

  @Test
  public void testSessionExpireLogic() throws Exception {
    String data1 = "testdata";
    zkClient.get().create(nodePath, data1.getBytes(), ACL, CreateMode.PERSISTENT);
    Listener<String> listener = new Listener<String>();
    ZooKeeperNode<String> zkNode = makeNode(nodePath, listener);

    assertEquals(data1, listener.waitForUpdate());

    expireSession(zkClient);
    assertEquals(data1, listener.waitForUpdate());

    String data2 = "avewf";
    zkClient = createZkClient();
    zkClient.get().setData(nodePath, data2.getBytes(), -1);
    assertEquals(data2, listener.waitForUpdate());
  }

  @Test
  public void testStaticCreate() throws Exception {
    String data = "stuff";
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    ZooKeeperNode<String> zkNode = ZooKeeperNode.create(zkClient, nodePath, BYTES_TO_STRING);
    assertEquals(data, zkNode.get());
  }

  private ZooKeeperNode<String> makeNode(String path, Closure<String> listener) throws Exception {
    ZooKeeperNode<String> zkNode = makeUninitializedNode(path, listener);
    zkNode.init();
    return zkNode;
  }

  private ZooKeeperNode<String> makeUninitializedNode(
      String path, Closure<String> listener) throws Exception {
    return new ZooKeeperNode<String>(zkClient, path, BYTES_TO_STRING, listener);
  }
}
