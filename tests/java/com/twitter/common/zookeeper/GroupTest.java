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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.ImmutableList;

import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.ACL;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.Command;
import com.twitter.common.base.Supplier;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.EasyMockTest;
import com.twitter.common.zookeeper.Group.GroupChangeListener;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.Group.Membership;
import com.twitter.common.zookeeper.ZooKeeperClient.Credentials;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;

import static com.google.common.testing.junit4.JUnitAsserts.assertNotEqual;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class GroupTest extends BaseZooKeeperTest {

  private ZooKeeperClient zkClient;
  private Group group;
  private com.twitter.common.base.Command onLoseMembership;

  private RecordingListener listener;

  public GroupTest() {
    super(Amount.of(1, Time.DAYS));
  }

  @Before
  public void mySetUp() throws Exception {
    onLoseMembership = createMock(Command.class);

    zkClient = createZkClient("group", "test");
    group = new Group(zkClient, ZooKeeperUtils.EVERYONE_READ_CREATOR_ALL, "/a/group");

    listener = new RecordingListener();
    group.watch(listener);
  }

  private static class RecordingListener implements GroupChangeListener {
    private final LinkedBlockingQueue<Iterable<String>> membershipChanges =
        new LinkedBlockingQueue<Iterable<String>>();

    @Override
    public void onGroupChange(Iterable<String> memberIds) {
      membershipChanges.add(memberIds);
    }

    public Iterable<String> take() throws InterruptedException {
      return membershipChanges.take();
    }

    public boolean isEmpty() {
      return membershipChanges.isEmpty();
    }

    @Override
    public String toString() {
      return membershipChanges.toString();
    }
  }

  private static class CustomNamingScheme implements Group.NodeNameScheme {
    public static final String NODENAME = "custom_name";

    private Predicate<String> nodeNameFilter;

    public CustomNamingScheme() {
      final Pattern groupNodeNamePattern = Pattern.compile("^" + Pattern.quote(NODENAME));
      nodeNameFilter = new Predicate<String>() {
          @Override public boolean apply(String childNodeName) {
            return groupNodeNamePattern.matcher(childNodeName).matches();
          }
      };
    }

    @Override
    public Predicate<String> getNodeNameFilter() {
      return nodeNameFilter;
    }

    @Override
    public String createNodePath(ZooKeeperClient zkClient, String path, byte[] membershipData,
        ImmutableList<ACL> acl) throws ZooKeeperConnectionException, KeeperException,
           InterruptedException {
      return zkClient.get().create(path + "/" + NODENAME, membershipData, acl,
          CreateMode.EPHEMERAL);

    }

    @Override
    public String extractMemberId(String nodePath) {
      String memberId = StringUtils.substringAfterLast(nodePath, "/");
      return memberId;
    }
  }

  @Test
  public void testLenientPaths() {
    assertEquals("/", Group.normalizePath("///"));
    assertEquals("/a/group", Group.normalizePath("/a/group"));
    assertEquals("/a/group", Group.normalizePath("/a/group/"));
    assertEquals("/a/group", Group.normalizePath("/a//group"));
    assertEquals("/a/group", Group.normalizePath("/a//group//"));

    try {
      Group.normalizePath("a/group");
      fail("Relative paths should not be allowed.");
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      Group.normalizePath("/a/./group");
      fail("Relative paths should not be allowed.");
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      Group.normalizePath("/a/../group");
      fail("Relative paths should not be allowed.");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void testSessionExpirationTriggersOnLoseMembership() throws Exception {
    final CountDownLatch lostMembership = new CountDownLatch(1);
    Command onLoseMembership = new Command() {
      @Override public void execute() throws RuntimeException {
        lostMembership.countDown();
      }
    };
    assertEmptyMembershipObserved();

    Membership membership = group.join(onLoseMembership);
    assertMembershipObserved(membership.getMemberId());
    expireSession(zkClient);

    lostMembership.await(); // Will hang this test if onLoseMembership event is not propagated.
  }

  @Test
  public void testNodeDeleteTriggersOnLoseMembership() throws Exception {
    final CountDownLatch lostMembership = new CountDownLatch(1);
    Command onLoseMembership = new Command() {
      @Override public void execute() throws RuntimeException {
        lostMembership.countDown();
      }
    };
    assertEmptyMembershipObserved();

    Membership membership = group.join(onLoseMembership);
    assertMembershipObserved(membership.getMemberId());
    membership.cancel();

    lostMembership.await(); // Will hang this test if onLoseMembership event is not propagated.
  }

  @Test
  public void testJoinsAndWatchesSurviveDisconnect() throws Exception {
    replay(onLoseMembership);

    assertEmptyMembershipObserved();

    Membership membership = group.join();
    String originalMemberId = membership.getMemberId();
    assertMembershipObserved(originalMemberId);

    shutdownNetwork();
    restartNetwork();

    // The member should still be present under existing ephemeral node since session did not
    // expire.
    group.watch(listener);
    assertMembershipObserved(originalMemberId);

    membership.cancel();

    assertEmptyMembershipObserved();
    assertEmptyMembershipObserved(); // and again for 2nd listener

    assertTrue(listener.isEmpty());

    verify(onLoseMembership);
    reset(onLoseMembership); // Turn off expectations during ZK server shutdown.
  }

  @Test
  public void testJoinsAndWatchesSurviveExpiredSession() throws Exception {
    onLoseMembership.execute();
    replay(onLoseMembership);

    assertEmptyMembershipObserved();

    Membership membership = group.join(onLoseMembership);
    String originalMemberId = membership.getMemberId();
    assertMembershipObserved(originalMemberId);

    expireSession(zkClient);

    // We should have lost our group membership and then re-gained it with a new ephemeral node.
    // We may or may-not see the intermediate state change but we must see the final state
    Iterable<String> members = listener.take();
    if (Iterables.isEmpty(members)) {
      members = listener.take();
    }
    assertEquals(1, Iterables.size(members));
    assertNotEqual(originalMemberId, Iterables.getOnlyElement(members));
    assertNotEqual(originalMemberId, membership.getMemberId());

    assertTrue(listener.isEmpty());

    verify(onLoseMembership);
    reset(onLoseMembership); // Turn off expectations during ZK server shutdown.
  }

  @Test
  public void testJoinCustomNamingScheme() throws Exception {
    group = new Group(zkClient, ZooKeeperUtils.EVERYONE_READ_CREATOR_ALL, "/a/group", new CustomNamingScheme());

    listener = new RecordingListener();
    group.watch(listener);
    assertEmptyMembershipObserved();

    Membership membership = group.join();
    String memberId = membership.getMemberId();

    assertEquals("Wrong member ID.", CustomNamingScheme.NODENAME, memberId);
    assertMembershipObserved(memberId);

    expireSession(zkClient);
  }

  @Test
  public void testUpdateMembershipData() throws Exception {
    Supplier<byte[]> dataSupplier = new EasyMockTest.Clazz<Supplier<byte[]>>() {}.createMock();

    byte[] initial = "start".getBytes();
    expect(dataSupplier.get()).andReturn(initial);

    byte[] second = "update".getBytes();
    expect(dataSupplier.get()).andReturn(second);

    replay(dataSupplier);

    Membership membership = group.join(dataSupplier, onLoseMembership);
    assertArrayEquals("Initial setting is incorrect.", initial, zkClient.get()
        .getData(membership.getMemberPath(), false, null));

    assertArrayEquals("Updating supplier should not change membership data",
        initial, zkClient.get().getData(membership.getMemberPath(), false, null));

    membership.updateMemberData();
    assertArrayEquals("Updating membership should change data",
        second, zkClient.get().getData(membership.getMemberPath(), false, null));

    verify(dataSupplier);
  }

  @Test
  public void testAcls() throws Exception {
    Group securedMembership =
        new Group(createZkClient("secured", "group"), ZooKeeperUtils.EVERYONE_READ_CREATOR_ALL,
            "/secured/group/membership");

    String memberId = securedMembership.join().getMemberId();

    Group unauthenticatedObserver =
        new Group(createZkClient(Credentials.NONE),
            Ids.READ_ACL_UNSAFE,
            "/secured/group/membership");
    RecordingListener unauthenticatedListener = new RecordingListener();
    unauthenticatedObserver.watch(unauthenticatedListener);

    assertMembershipObserved(unauthenticatedListener, memberId);

    try {
      unauthenticatedObserver.join();
      fail("Expected join exception for unauthenticated observer");
    } catch (JoinException e) {
      // expected
    }

    Group unauthorizedObserver =
        new Group(createZkClient("joe", "schmoe"),
            Ids.READ_ACL_UNSAFE,
            "/secured/group/membership");
    RecordingListener unauthorizedListener = new RecordingListener();
    unauthorizedObserver.watch(unauthorizedListener);

    assertMembershipObserved(unauthorizedListener, memberId);

    try {
      unauthorizedObserver.join();
      fail("Expected join exception for unauthorized observer");
    } catch (JoinException e) {
      // expected
    }
  }

  private void assertEmptyMembershipObserved() throws InterruptedException {
    Iterable<String> membershipChange = listener.take();
    assertTrue("Expected an empty membershipChange, got: " + membershipChange + " queued: " +
               listener,
        Iterables.isEmpty(membershipChange));
  }

  private void assertMembershipObserved(String expectedMemberId) throws InterruptedException {
    assertMembershipObserved(listener, expectedMemberId);
  }

  private void assertMembershipObserved(RecordingListener listener, String expectedMemberId)
      throws InterruptedException {
    Iterable<String> members = listener.take();
    assertEquals(1, Iterables.size(members));
    assertEquals(expectedMemberId, Iterables.getOnlyElement(members));
  }
}
