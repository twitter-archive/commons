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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.common.collect.Iterables;

import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.Command;
import com.twitter.common.base.Supplier;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.EasyMockTest;
import com.twitter.common.zookeeper.Group.GroupChangeListener;
import com.twitter.common.zookeeper.Group.Membership;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;

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

  private static final List<ACL> ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;

  private ZooKeeperClient zkClient;
  private Group group;
  private com.twitter.common.base.Command onLoseMembership;
  private LinkedBlockingQueue<Iterable<String>> membershipChanges;
  private GroupChangeListener listener;

  @Before
  public void mySetUp() throws Exception {
    onLoseMembership = createMock(Command.class);

    zkClient = createZkClient(Amount.of(1, Time.MINUTES));
    group = new Group(zkClient, ACL, "/a/group");

    membershipChanges = new LinkedBlockingQueue<Iterable<String>>();
    listener = new RecordingListener();
    group.watch(listener);
  }

  private class RecordingListener implements GroupChangeListener {
    @Override
    public void onGroupChange(Iterable<String> memberIds) {
      membershipChanges.add(memberIds);
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

    assertTrue(membershipChanges.isEmpty());

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
    Iterable<String> members = membershipChanges.take();
    if (Iterables.isEmpty(members)) {
      members = membershipChanges.take();
    }
    assertEquals(1, Iterables.size(members));
    assertNotEqual(originalMemberId, Iterables.getOnlyElement(members));
    assertNotEqual(originalMemberId, membership.getMemberId());

    assertTrue(membershipChanges.isEmpty());

    verify(onLoseMembership);
    reset(onLoseMembership); // Turn off expectations during ZK server shutdown.
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

  private void assertEmptyMembershipObserved() throws InterruptedException {
    Iterable<String> membershipChange = membershipChanges.take();
    assertTrue("Expected an empty membershipChange, got: " + membershipChange + " queued: " +
               membershipChanges,
        Iterables.isEmpty(membershipChange));
  }

  private void assertMembershipObserved(String expectedMemberId) throws InterruptedException {
    Iterable<String> members = membershipChanges.take();
    assertEquals(1, Iterables.size(members));
    assertEquals(expectedMemberId, Iterables.getOnlyElement(members));
  }
}
