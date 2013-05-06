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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Ordering;

import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.zookeeper.Candidate.Leader;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CandidateImplTest extends BaseZooKeeperTest {
  private static final List<ACL> ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;
  private static final String SERVICE = "/twitter/services/puffin_linkhose/leader";
  private static final Amount<Integer, Time> TIMEOUT = Amount.of(1, Time.MINUTES);

  private LinkedBlockingDeque<CandidateImpl> candidateBuffer;

  @Before
  public void mySetUp() throws IOException {
    candidateBuffer = new LinkedBlockingDeque<CandidateImpl>();
  }

  private Group createGroup(ZooKeeperClient zkClient) throws IOException {
    return new Group(zkClient, ACL, SERVICE);
  }

  private class Reign implements Leader {
    private ExceptionalCommand<JoinException> abdicate;
    private final CandidateImpl candidate;
    private final String id;
    private CountDownLatch defeated = new CountDownLatch(1);

    Reign(String id, CandidateImpl candidate) {
      this.id = id;
      this.candidate = candidate;
    }

    @Override
    public void onElected(ExceptionalCommand<JoinException> abdicate) {
      candidateBuffer.offerFirst(candidate);
      this.abdicate = abdicate;
    }

    @Override
    public void onDefeated() {
      defeated.countDown();
    }

    public void abdicate() throws JoinException {
      Preconditions.checkState(abdicate != null);
      abdicate.execute();
    }

    public void expectDefeated() throws InterruptedException {
      defeated.await();
    }

    @Override
    public String toString() {
      return id;
    }
  }

  @Test
  public void testOfferLeadership() throws Exception {
    ZooKeeperClient zkClient1 = createZkClient(TIMEOUT);
    final CandidateImpl candidate1 = new CandidateImpl(createGroup(zkClient1)) {
      @Override public String toString() {
        return "Leader1";
      }
    };
    ZooKeeperClient zkClient2 = createZkClient(TIMEOUT);
    final CandidateImpl candidate2 = new CandidateImpl(createGroup(zkClient2)) {
      @Override public String toString() {
        return "Leader2";
      }
    };
    ZooKeeperClient zkClient3 = createZkClient(TIMEOUT);
    final CandidateImpl candidate3 = new CandidateImpl(createGroup(zkClient3)) {
      @Override public String toString() {
        return "Leader3";
      }
    };

    Reign candidate1Reign = new Reign("1", candidate1);
    Reign candidate2Reign = new Reign("2", candidate2);
    Reign candidate3Reign = new Reign("3", candidate3);

    Supplier<Boolean> candidate1Leader = candidate1.offerLeadership(candidate1Reign);
    Supplier<Boolean> candidate2Leader = candidate2.offerLeadership(candidate2Reign);
    Supplier<Boolean> candidate3Leader = candidate3.offerLeadership(candidate3Reign);

    assertTrue("Since initial group join is synchronous, candidate 1 should be the first leader",
        candidate1Leader.get());

    shutdownNetwork();
    restartNetwork();

    assertTrue("A re-connect without a session expiration should leave the leader elected",
        candidate1Leader.get());

    candidate1Reign.abdicate();
    assertSame(candidate1, candidateBuffer.takeLast());
    assertFalse(candidate1Leader.get());
    // Active abdication should trigger defeat.
    candidate1Reign.expectDefeated();

    CandidateImpl secondCandidate = candidateBuffer.takeLast();
    assertTrue("exactly 1 remaining candidate should now be leader: " + secondCandidate + " "
               + candidateBuffer,
        candidate2Leader.get() ^ candidate3Leader.get());

    if (secondCandidate == candidate2) {
      expireSession(zkClient2);
      assertSame(candidate3, candidateBuffer.takeLast());
      assertTrue(candidate3Leader.get());
      // Passive expiration should trigger defeat.
      candidate2Reign.expectDefeated();
    } else {
      expireSession(zkClient3);
      assertSame(candidate2, candidateBuffer.takeLast());
      assertTrue(candidate2Leader.get());
      // Passive expiration should trigger defeat.
      candidate3Reign.expectDefeated();
    }
  }

  @Test
  public void testCustomJudge() throws Exception {
    Function<Iterable<String>, String> judge = new Function<Iterable<String>, String>() {
      @Override public String apply(Iterable<String> input) {
        return Ordering.natural().max(input);
      }
    };

    ZooKeeperClient zkClient1 = createZkClient(TIMEOUT);
    Group group1 = createGroup(zkClient1);
    final CandidateImpl candidate1 =
        new CandidateImpl(group1, judge, Suppliers.ofInstance("Leader1".getBytes())) {
          @Override public String toString() {
            return "Leader1";
          }
        };
    ZooKeeperClient zkClient2 = createZkClient(TIMEOUT);
    Group group2 = createGroup(zkClient2);
    final CandidateImpl candidate2 =
        new CandidateImpl(group2, judge, Suppliers.ofInstance("Leader2".getBytes())) {
          @Override public String toString() {
            return "Leader2";
          }
        };

    Reign candidate1Reign = new Reign("1", candidate1);
    Reign candidate2Reign = new Reign("2", candidate2);

    candidate1.offerLeadership(candidate1Reign);
    assertSame(candidate1, candidateBuffer.takeLast());

    Supplier<Boolean> candidate2Leader = candidate2.offerLeadership(candidate2Reign);
    assertSame(candidate2, candidateBuffer.takeLast());
    candidate1Reign.expectDefeated();
    assertTrue("Since the judge picks the newest member joining a group as leader candidate 1 "
               + "should be defeated and candidate 2 leader", candidate2Leader.get());
  }

  @Test
  public void testCustomDataSupplier() throws Exception {
    byte[] DATA = "Leader1".getBytes();
    ZooKeeperClient zkClient1 = createZkClient(TIMEOUT);
    Group group1 = createGroup(zkClient1);
    CandidateImpl candidate1 = new CandidateImpl(group1, Suppliers.ofInstance(DATA)) {
      @Override public String toString() {
        return "Leader1";
      }
    };
    Reign candidate1Reign = new Reign("1", candidate1);

    Supplier<Boolean> candidate1Leader = candidate1.offerLeadership(candidate1Reign);
    assertSame(candidate1, candidateBuffer.takeLast());
    assertTrue(candidate1Leader.get());
    assertArrayEquals(DATA, candidate1.getLeaderData().get());
  }

  @Test
  public void testEmptyMembership() throws Exception {
    ZooKeeperClient zkClient1 = createZkClient(TIMEOUT);
    final CandidateImpl candidate1 = new CandidateImpl(createGroup(zkClient1));
    Reign candidate1Reign = new Reign("1", candidate1);

    candidate1.offerLeadership(candidate1Reign);
    assertSame(candidate1, candidateBuffer.takeLast());
    candidate1Reign.abdicate();
    assertFalse(candidate1.getLeaderData().isPresent());
  }
}
