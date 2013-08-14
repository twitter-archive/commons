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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.apache.zookeeper.ZooDefs.Ids;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.Command;
import com.twitter.common.base.Supplier;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.zookeeper.Group.GroupChangeListener;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.Group.Membership;
import com.twitter.common.zookeeper.Group.NodeScheme;
import com.twitter.common.zookeeper.ZooKeeperClient.Credentials;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;

import static com.google.common.testing.junit4.JUnitAsserts.assertNotEqual;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GroupTest extends BaseZooKeeperTest {

  private ZooKeeperClient zkClient;
  private Group joinGroup;
  private Group watchGroup;
  private Command stopWatching;
  private com.twitter.common.base.Command onLoseMembership;

  private RecordingListener listener;

  public GroupTest() {
    super(Amount.of(1, Time.DAYS));
  }

  @Before
  public void mySetUp() throws Exception {
    onLoseMembership = createMock(Command.class);

    zkClient = createZkClient("group", "test");
    joinGroup = new Group(zkClient, ZooKeeperUtils.EVERYONE_READ_CREATOR_ALL, "/a/group");
    watchGroup = new Group(zkClient, ZooKeeperUtils.EVERYONE_READ_CREATOR_ALL, "/a/group");

    listener = new RecordingListener();
    stopWatching = watchGroup.watch(listener);
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

    public void assertEmpty() {
      assertEquals(ImmutableList.<Iterable<String>>of(), ImmutableList.copyOf(membershipChanges));
    }

    @Override
    public String toString() {
      return membershipChanges.toString();
    }
  }

  private static class CustomScheme implements NodeScheme {
    static final String NODE_NAME = "custom_name";

    @Override
    public boolean isMember(String nodeName) {
      return NODE_NAME.equals(nodeName);
    }

    @Override
    public String createName(byte[] membershipData) {
      return NODE_NAME;
    }

    @Override
    public boolean isSequential() {
      return false;
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

    Membership membership = joinGroup.join(onLoseMembership);
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

    Membership membership = joinGroup.join(onLoseMembership);
    assertMembershipObserved(membership.getMemberId());
    membership.cancel();

    lostMembership.await(); // Will hang this test if onLoseMembership event is not propagated.
  }

  @Test
  public void testJoinsAndWatchesSurviveDisconnect() throws Exception {
    replay(onLoseMembership);

    assertEmptyMembershipObserved();

    Membership membership = joinGroup.join();
    String originalMemberId = membership.getMemberId();
    assertMembershipObserved(originalMemberId);

    shutdownNetwork();
    restartNetwork();

    // The member should still be present under existing ephemeral node since session did not
    // expire.
    watchGroup.watch(listener);
    assertMembershipObserved(originalMemberId);

    membership.cancel();

    assertEmptyMembershipObserved();
    assertEmptyMembershipObserved(); // and again for 2nd listener

    listener.assertEmpty();

    verify(onLoseMembership);
    reset(onLoseMembership); // Turn off expectations during ZK server shutdown.
  }

  @Test
  public void testJoinsAndWatchesSurviveExpiredSession() throws Exception {
    onLoseMembership.execute();
    replay(onLoseMembership);

    assertEmptyMembershipObserved();

    Membership membership = joinGroup.join(onLoseMembership);
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

    listener.assertEmpty();

    verify(onLoseMembership);
    reset(onLoseMembership); // Turn off expectations during ZK server shutdown.
  }

  @Test
  public void testJoinCustomNamingScheme() throws Exception {
    Group group = new Group(zkClient, ZooKeeperUtils.EVERYONE_READ_CREATOR_ALL, "/a/group",
        new CustomScheme());

    listener = new RecordingListener();
    group.watch(listener);
    assertEmptyMembershipObserved();

    Membership membership = group.join();
    String memberId = membership.getMemberId();

    assertEquals("Wrong member ID.", CustomScheme.NODE_NAME, memberId);
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

    Membership membership = joinGroup.join(dataSupplier, onLoseMembership);
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

  @Test
  public void testStopWatching() throws Exception {
    replay(onLoseMembership);

    assertEmptyMembershipObserved();

    Membership member1 = joinGroup.join();
    String memberId1 = member1.getMemberId();
    assertMembershipObserved(memberId1);

    Membership member2 = joinGroup.join();
    String memberId2 = member2.getMemberId();
    assertMembershipObserved(memberId1, memberId2);

    stopWatching.execute();

    member1.cancel();
    Membership member3 = joinGroup.join();
    member2.cancel();
    member3.cancel();

    listener.assertEmpty();
  }

  private void assertEmptyMembershipObserved() throws InterruptedException {
    assertMembershipObserved();
  }

  private void assertMembershipObserved(String... expectedMemberIds) throws InterruptedException {
    assertMembershipObserved(listener, expectedMemberIds);
  }

  private void assertMembershipObserved(RecordingListener listener, String... expectedMemberIds)
      throws InterruptedException {

    assertEquals(ImmutableSet.copyOf(expectedMemberIds), ImmutableSet.copyOf(listener.take()));
  }
}
