package com.twitter.common.zookeeper;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.Closure;
import com.twitter.common.base.Closures;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ZooKeeperNodeTest extends BaseZooKeeperTest {

  private static final List<ACL> ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;

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
    TestDeserializer deserializer = new TestDeserializer();
    makeNode(deserializer, nodePath, listener);

    assertEquals(data1, listener.waitForUpdate());
    assertNotNull(deserializer.getStat());
    assertEquals(0, deserializer.getStat().getVersion());
    String data2 = "BLAH";
    zkClient.get().setData(nodePath, data2.getBytes(), -1);
    assertEquals(data2, listener.waitForUpdate());
    assertEquals(1, deserializer.getStat().getVersion());
  }

  @Test
  public void testRemoveNode() throws Exception {
    String data = "testdata";
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    Listener<String> listener = new Listener<String>();
    TestDeserializer deserializer = new TestDeserializer();
    makeNode(deserializer, nodePath, listener);

    assertEquals(data, listener.waitForUpdate());
    assertNotNull(deserializer.getStat());
    assertEquals(0, deserializer.getStat().getVersion());

    zkClient.get().delete(nodePath, -1);
    assertEquals(null, listener.waitForUpdate());
    assertEquals(0, deserializer.getStat().getVersion());

    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    assertEquals(data, listener.waitForUpdate());
    assertEquals(0, deserializer.getStat().getVersion());
  }

  @Test
  public void testSessionExpireLogic() throws Exception {
    String data1 = "testdata";
    zkClient.get().create(nodePath, data1.getBytes(), ACL, CreateMode.PERSISTENT);
    Listener<String> listener = new Listener<String>();
    TestDeserializer deserializer = new TestDeserializer();
    makeNode(deserializer, nodePath, listener);

    assertEquals(data1, listener.waitForUpdate());
    assertNotNull(deserializer.getStat());
    assertEquals(0, deserializer.getStat().getVersion());

    expireSession(zkClient);
    assertEquals(data1, listener.waitForUpdate());

    String data2 = "avewf";
    zkClient = createZkClient();
    zkClient.get().setData(nodePath, data2.getBytes(), -1);
    assertEquals(data2, listener.waitForUpdate());
    assertEquals(1, deserializer.getStat().getVersion());
  }

  @Test
  public void testStaticCreate() throws Exception {
    String data = "stuff";
    zkClient.get().create(nodePath, data.getBytes(), ACL, CreateMode.PERSISTENT);
    ZooKeeperNode<String> zkNode = ZooKeeperNode.create(zkClient, nodePath, new TestDeserializer());
    assertEquals(data, zkNode.get());
  }

  private ZooKeeperNode<String> makeNode(TestDeserializer deserializer, String path,
      Closure<String> listener) throws Exception {
    ZooKeeperNode<String> zkNode = makeUninitializedNode(deserializer, path, listener);
    zkNode.init();
    return zkNode;
  }

  private ZooKeeperNode<String> makeUninitializedNode(String path, Closure<String> listener)
      throws Exception {
    return makeUninitializedNode(new TestDeserializer(), path, listener);
  }

  private ZooKeeperNode<String> makeUninitializedNode(
      ZooKeeperNode.NodeDeserializer<String> deserializer, String path, Closure<String> listener)
      throws Exception {
    // we test deserializertionWithPair primarily because it is deserializertionally a proper
    // superset of deserializertionWithByteArray
    return new ZooKeeperNode<String>(zkClient, path, deserializer, listener);
  }

  // helper to test Stat population and retrieval
  private static final class TestDeserializer implements ZooKeeperNode.NodeDeserializer<String> {
    private Stat stat = null;

    @Override
    public String deserialize(byte[] data, Stat stat) {
      this.stat = stat;
      return new String(data);
    }

    Stat getStat() {
      return stat;
    }
  }
}
