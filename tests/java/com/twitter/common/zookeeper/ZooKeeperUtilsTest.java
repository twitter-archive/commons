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

import com.google.common.base.Charsets;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.BadVersionException;
import org.apache.zookeeper.KeeperException.NoAuthException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;

import static com.google.common.testing.junit4.JUnitAsserts.assertNotEqual;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class ZooKeeperUtilsTest extends BaseZooKeeperTest {
  @Test
  public void testEnsurePath() throws Exception {
    ZooKeeperClient zkClient = createZkClient();
    zkClient.get().addAuthInfo("digest", "client1:boo".getBytes(Charsets.UTF_8));

    assertNull(zkClient.get().exists("/foo", false));
    ZooKeeperUtils.ensurePath(zkClient, ZooDefs.Ids.CREATOR_ALL_ACL, "/foo/bar/baz");

    zkClient = createZkClient();
    zkClient.get().addAuthInfo("digest", "client2:bap".getBytes(Charsets.UTF_8));

    // Anyone can check for existence in ZK
    assertNotNull(zkClient.get().exists("/foo", false));
    assertNotNull(zkClient.get().exists("/foo/bar", false));
    assertNotNull(zkClient.get().exists("/foo/bar/baz", false));

    try {
      zkClient.get().delete("/foo/bar/baz", -1 /* delete no matter what */);
      fail("Expected CREATOR_ALL_ACL to be applied to created path and client2 mutations to be "
           + "rejected");
    } catch (NoAuthException e) {
      // expected
    }
  }

  @Test
  public void testMagicVersionNumberAllowsUnconditionalUpdate() throws Exception {
    String nodePath = "/foo";
    ZooKeeperClient zkClient = createZkClient();

    zkClient.get().create(nodePath, "init".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
        CreateMode.PERSISTENT);

    Stat initStat = new Stat();
    byte[] initialData = zkClient.get().getData(nodePath, false, initStat);
    assertArrayEquals("init".getBytes(), initialData);

    // bump the version
    Stat rev1Stat = zkClient.get().setData(nodePath, "rev1".getBytes(), initStat.getVersion());

    try {
      zkClient.get().setData(nodePath, "rev2".getBytes(), initStat.getVersion());
      fail("expected correct version to be required");
    } catch (BadVersionException e) {
      // expected
    }

    // expect using the correct version to work
    Stat rev2Stat = zkClient.get().setData(nodePath, "rev2".getBytes(), rev1Stat.getVersion());
    assertNotEqual(ZooKeeperUtils.ANY_VERSION, rev2Stat.getVersion());

    zkClient.get().setData(nodePath, "force-write".getBytes(), ZooKeeperUtils.ANY_VERSION);
    Stat forceWriteStat = new Stat();
    byte[] forceWriteData = zkClient.get().getData(nodePath, false, forceWriteStat);
    assertArrayEquals("force-write".getBytes(), forceWriteData);

    assertTrue(forceWriteStat.getVersion() > rev2Stat.getVersion());
    assertNotEqual(ZooKeeperUtils.ANY_VERSION, forceWriteStat.getVersion());
  }

  @Test
  public void testNormalizingPath() throws Exception {
    assertEquals("/", ZooKeeperUtils.normalizePath("/"));
    assertEquals("/foo", ZooKeeperUtils.normalizePath("/foo/"));
    assertEquals("/foo/bar", ZooKeeperUtils.normalizePath("/foo//bar"));
    assertEquals("/foo/bar", ZooKeeperUtils.normalizePath("//foo/bar"));
    assertEquals("/foo/bar", ZooKeeperUtils.normalizePath("/foo/bar/"));
    assertEquals("/foo/bar", ZooKeeperUtils.normalizePath("/foo/bar//"));
    assertEquals("/foo/bar", ZooKeeperUtils.normalizePath("/foo/bar"));
  }

  @Test
  public void testLenientPaths() {
    assertEquals("/", ZooKeeperUtils.normalizePath("///"));
    assertEquals("/a/group", ZooKeeperUtils.normalizePath("/a/group"));
    assertEquals("/a/group", ZooKeeperUtils.normalizePath("/a/group/"));
    assertEquals("/a/group", ZooKeeperUtils.normalizePath("/a//group"));
    assertEquals("/a/group", ZooKeeperUtils.normalizePath("/a//group//"));

    try {
      ZooKeeperUtils.normalizePath("a/group");
      fail("Relative paths should not be allowed.");
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      ZooKeeperUtils.normalizePath("/a/./group");
      fail("Relative paths should not be allowed.");
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      ZooKeeperUtils.normalizePath("/a/../group");
      fail("Relative paths should not be allowed.");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

}
