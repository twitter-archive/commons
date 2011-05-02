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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.twitter.common.collections.Pair;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.junit.Before;
import org.junit.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Adam Samet
 */
public class ZooKeeperMapTest extends BaseZooKeeperTest {

  private static final List<ACL> ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;
  private static final Function<byte[], String> BYTES_TO_STRING =
      new Function<byte[], String>() {
        @Override
        public String apply(byte[] from) {
          return new String(from);
        }};

  private ZooKeeperClient zkClient;
  private BlockingQueue<Pair<String, String>> entryChanges;

  @Before
  public void mySetUp() throws Exception {
    zkClient = createZkClient();
    entryChanges = new LinkedBlockingQueue<Pair<String, String>>();
  }

  @Test(expected = KeeperException.NoNodeException.class)
  public void testMissingPath() throws Exception {
    makeMap("/twitter/doesnt/exist");
  }

  @Test(expected = KeeperException.class)
  public void testZooKeeperUnavailableAtConstruction() throws Exception {
    final String parentPath = "/twitter/path";
    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);

    shutdownNetwork();  // Make zk unavailable.

    ZooKeeperMap zkMap = makeUninitializedMap(parentPath);
  }

  @Test(expected = KeeperException.class)
  public void testZooKeeperUnavailableAtInit() throws Exception {
    final String parentPath = "/twitter/path";
    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    ZooKeeperMap zkMap = makeUninitializedMap(parentPath);

    shutdownNetwork();  // Make zk unavailable.

    zkMap.init();
  }

  @Test
  public void testInitialization() throws Exception {
    final String parentPath = "/twitter/path";
    final String node = "node";
    final String nodePath = parentPath + "/" + node;
    final String data = "abcdefg";

    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    ZooKeeperMap zkMap = makeUninitializedMap(parentPath);

    // Map should be empty before initialization
    assertTrue(zkMap.isEmpty());

    zkMap.init();

    // Now that we've initialized, the data should be synchronously reflected.
    assertFalse(zkMap.isEmpty());
    assertEquals(1, zkMap.size());
    assertEquals(data, zkMap.get(node));
  }

  @Test
  public void testEmptyStaticMap() throws Exception {
    final String parentPath = "/twitter/path";
    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    Map<String, String> zkMap = makeMap(parentPath);

    assertEquals(0, zkMap.size());
    assertTrue(zkMap.isEmpty());
  }

  @Test
  public void testStaticMapWithValues() throws Exception {
    final String parentPath = "/twitter/path";
    final String node1 = "node1";
    final String node2 = "node2";
    final String nodePath1 = parentPath + "/" + node1;
    final String nodePath2 = parentPath + "/" + node2;
    final String data1 = "hello World!";
    final String data2 = "evrver232&$";
    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    zkClient.get().create(nodePath1, data1.getBytes(), ACL, CreateMode.PERSISTENT);
    zkClient.get().create(nodePath2, data2.getBytes(), ACL, CreateMode.PERSISTENT);

    Map<String, String> zkMap = makeMap(parentPath);

    // Test all java.util.Map operations that are implemented.
    assertTrue(zkMap.containsKey(node1));
    assertTrue(zkMap.containsKey(node2));
    assertTrue(zkMap.containsValue(data1));
    assertTrue(zkMap.containsValue(data2));
    assertEquals(ImmutableSet.of(new SimpleEntry(node1, data1),
        new SimpleEntry(node2, data2)), zkMap.entrySet());
    assertEquals(data1, zkMap.get(node1));
    assertEquals(data2, zkMap.get(node2));
    assertFalse(zkMap.isEmpty());
    assertEquals(ImmutableSet.of(node1, node2),
        zkMap.keySet());
    assertEquals(2, zkMap.size());
  }

  @Test
  public void testChangingChildren() throws Exception {
    final String parentPath = "/twitter/path";
    final String node1 = "node1";
    final String node2 = "node2";
    final String nodePath1 = parentPath + "/" + node1;
    final String nodePath2 = parentPath + "/" + node2;
    final String data1 = "wefwe";
    final String data2 = "rtgrtg";

    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    zkClient.get().create(nodePath1, data1.getBytes(), ACL, CreateMode.PERSISTENT);

    Map<String, String> zkMap = makeMap(parentPath);
    assertEquals(1, zkMap.size());
    assertEquals(data1, zkMap.get(node1));
    assertEquals(null, zkMap.get(node2));

    // Make sure the map is updated when a child is added.
    zkClient.get().create(nodePath2, data2.getBytes(), ACL, CreateMode.PERSISTENT);
    waitForEntryChange(node2, data2);
    assertEquals(2, zkMap.size());
    assertEquals(data1, zkMap.get(node1));
    assertEquals(data2, zkMap.get(node2));

    // Make sure the map is updated when a child is deleted.
    zkClient.get().delete(nodePath1, -1);
    waitForEntryChange(node1, null);
    assertEquals(1, zkMap.size());
    assertEquals(null, zkMap.get(node1));
    assertEquals(data2, zkMap.get(node2));
  }

  @Test
  public void testChangingChildValues() throws Exception {
    final String parentPath = "/twitter/path";
    final String node = "node";
    final String nodePath = parentPath + "/" + node;

    final String data1 = "";
    final String data2 = "abc";
    final String data3 = "lalala";

    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    zkClient.get().create(nodePath, data1.getBytes(), ACL, CreateMode.PERSISTENT);

    Map<String, String> zkMap = makeMap(parentPath);

    assertEquals(1, zkMap.size());
    assertEquals(data1, zkMap.get(node));

    zkClient.get().setData(nodePath, data2.getBytes(), -1);
    waitForEntryChange(node, data2);
    assertEquals(1, zkMap.size());

    zkClient.get().setData(nodePath, data3.getBytes(), -1);
    waitForEntryChange(node, data3);
    assertEquals(1, zkMap.size());
  }

  @Test
  public void testRemoveParentNode() throws Exception {
    final String parentPath = "/twitter/path";
    final String node = "node";
    final String nodePath = parentPath + "/" + node;
    final String data = "testdata";

    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);

    Map<String, String> zkMap = makeMap(parentPath);
    assertEquals(1, zkMap.size());
    assertEquals(data, zkMap.get(node));

    zkClient.get().delete(nodePath, -1);
    zkClient.get().delete(parentPath, -1);

    waitForEntryChange(node, null);
    assertEquals(0, zkMap.size());
    assertTrue(zkMap.isEmpty());

    // Recreate our node, make sure the map observes it.
    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    waitForEntryChange(node, data);
  }

  @Test
  public void testSessionExpireLogic() throws Exception {
    final String parentPath = "/twitter/path";
    final String node1 = "node1";
    final String nodePath1 = parentPath + "/" + node1;
    final String data1 = "testdata";

    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    zkClient.get().create(nodePath1, data1.getBytes(), ACL, CreateMode.PERSISTENT);

    Map<String, String> zkMap = makeMap(parentPath);
    assertEquals(1, zkMap.size());
    assertEquals(data1, zkMap.get(node1));

    expireSession(zkClient);
    assertEquals(1, zkMap.size());
    assertEquals(data1, zkMap.get(node1));

    final String node2 = "node2";
    final String nodePath2 = parentPath + "/" + node2;
    final String data2 = "testdata2";
    zkClient = createZkClient();
    zkClient.get().create(nodePath2, data2.getBytes(), ACL, CreateMode.PERSISTENT);

    waitForEntryChange(node2, data2);
    assertEquals(2, zkMap.size());
    assertEquals(data2, zkMap.get(node2));
  }

  @Test
  public void testStaticCreate() throws Exception {
    String parentPath = "/twitter/path";
    String node = "node";
    String nodePath = parentPath + "/" + node;
    String data = "DaTa";

    ZooKeeperUtils.ensurePath(zkClient, ACL, parentPath);
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);

    Map<String, String> zkMap = ZooKeeperMap.create(zkClient, parentPath, BYTES_TO_STRING);
    assertEquals(1, zkMap.size());
    assertEquals(data, zkMap.get(node));
  }

  private void waitForEntryChange(String key, String value) throws Exception {
    Pair<String, String> expectedEntry = Pair.of(key, value);
    while (true) {
      Pair<String, String> nextEntry = entryChanges.take();
      if (expectedEntry.equals(nextEntry)) {
        return;
      }
    }
  }

  private Map<String, String> makeMap(String path) throws Exception {
    ZooKeeperMap<String> zkMap = makeUninitializedMap(path);
    zkMap.init();
    return zkMap;
  }

  private ZooKeeperMap<String> makeUninitializedMap(String path) throws Exception {
    return new ZooKeeperMap<String>(zkClient, path, BYTES_TO_STRING) {
      @Override void putEntry(String key, String value) {
        super.putEntry(key, value);
        recordEntryChange(key);
      }

      @Override void removeEntry(String key) {
        super.removeEntry(key);
        recordEntryChange(key);
      }

      private void recordEntryChange(String key) {
        entryChanges.offer(Pair.of(key, get(key)));
      }
    };
  }
}
