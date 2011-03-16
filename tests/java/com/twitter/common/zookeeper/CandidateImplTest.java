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

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.zookeeper.Candidate.Leader;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author John Sirois
 */
public class CandidateImplTest extends BaseZooKeeperTest {
  private static final List<ACL> ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;
  private static final String SERVICE = "/twitter/services/puffin_linkhose/leader";

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
    Amount<Integer, Time> timeout = Amount.of(1, Time.MINUTES);

    ZooKeeperClient zkClient1 = createZkClient(timeout);
    final CandidateImpl candidate1 = new CandidateImpl(createGroup(zkClient1)) {
      @Override public String toString() {
        return "Leader1";
      }
    };
    ZooKeeperClient zkClient2 = createZkClient(timeout);
    final CandidateImpl candidate2 = new CandidateImpl(createGroup(zkClient2)) {
      @Override public String toString() {
        return "Leader2";
      }
    };
    ZooKeeperClient zkClient3 = createZkClient(timeout);
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
}
