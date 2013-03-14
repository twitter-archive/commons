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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

import org.apache.zookeeper.KeeperException;

import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.zookeeper.Group.GroupChangeListener;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.Group.Membership;
import com.twitter.common.zookeeper.Group.WatchException;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;

/**
 * Implements leader election for small groups of candidates.  This implementation is subject to the
 * <a href="http://hadoop.apache.org/zookeeper/docs/r3.2.1/recipes.html#sc_leaderElection">
 * herd effect</a> for a given group and should only be used for small (~10 member) candidate pools.
 *
 * @author John Sirois
 */
public class CandidateImpl implements Candidate {
  private static final Logger LOG = Logger.getLogger(CandidateImpl.class.getName());

  private final Group group;

  private final Function<Iterable<String>, String> judge;
  private final Supplier<byte[]> dataSupplier;

  static final Supplier<byte[]> IP_ADDRESS_DATA_SUPPLIER = new Supplier<byte[]>() {
    @Override
    public byte[] get() {
      try {
        return InetAddress.getLocalHost().getAddress();
      } catch (UnknownHostException e) {
        LOG.log(Level.WARNING, "Failed to determine local address!", e);
        return new byte[0];
      }
    }
  };

  public static final Function<Iterable<String>, String> MOST_RECENT_JUDGE =
      new Function<Iterable<String>, String>() {
        @Override public String apply(Iterable<String> candidates) {
          return Ordering.natural().min(candidates);
        }
      };

  /**
   * Equivalent to {@link #CandidateImpl(Group, com.google.common.base.Function, Supplier)} using a judge that
   * always picks the lowest numbered candidate ephemeral node - by proxy the oldest or 1st
   * candidate and a default supplier that populates the data in the underlying znode with the byte representation
   * of the ip address according to {@link java.net.InetAddress#getLocalHost()}.
   */
  public CandidateImpl(Group group) {
    this(group, MOST_RECENT_JUDGE, IP_ADDRESS_DATA_SUPPLIER);
  }

  /**
   * Creates a candidate that can be used to offer leadership for the given {@code group} using
   * a judge that always picks the lowest numbered candidate ephemeral node - by proxy the oldest
   * or 1st. The dataSupplier is the source of the data that will be stored in the leader-znode
   * and which is available to all participants via the getLeaderData method.
   */
  public CandidateImpl(Group group, Supplier<byte[]> dataSupplier) {
    this(group, MOST_RECENT_JUDGE, dataSupplier);
  }

  /**
   * Creates a candidate that can be used to offer leadership for the given {@code group}.  The
   * {@code judge} is used to pick the current leader from all group members whenever the group
   * membership changes. To form a well-behaved election group with one leader, all candidates
   * should use the same judge. The dataSupplier is the source of the data that will be stored
   * in the leader-znode and which is available to all participants via the getLeaderData method.
   */
  public CandidateImpl(Group group, Function<Iterable<String>, String> judge, Supplier<byte[]> dataSupplier) {
    this.group = Preconditions.checkNotNull(group);
    this.judge = Preconditions.checkNotNull(judge);
    this.dataSupplier = Preconditions.checkNotNull(dataSupplier);
  }

  @Override
  public byte[] getLeaderData()
      throws ZooKeeperConnectionException, KeeperException, InterruptedException {

    String leaderId = getLeader(group.getMemberIds());
    if (leaderId == null) {
      return null;
    }

    byte[] data = group.getMemberData(leaderId);
    return data == null ? new byte[0] : data;
  }

  @Override
  public Supplier<Boolean> offerLeadership(final Leader leader)
      throws JoinException, WatchException, InterruptedException {

    final Membership membership = group.join(dataSupplier, new Command() {
      @Override public void execute() {
        leader.onDefeated();
      }
    });

    final AtomicBoolean elected = new AtomicBoolean(false);
    final AtomicBoolean abdicated = new AtomicBoolean(false);
    group.watch(new GroupChangeListener() {
        @Override public void onGroupChange(Iterable<String> memberIds) {
          boolean noCandidates = Iterables.isEmpty(memberIds);
          String memberId = membership.getMemberId();

          if (noCandidates) {
            LOG.warning("All candidates have temporarily left the group: " + group);
          } else if (!Iterables.contains(memberIds, memberId)) {
            LOG.severe(String.format(
                "Current member ID %s is not a candidate for leader, current voting: %s",
                memberId, memberIds));
          } else {
            boolean electedLeader = memberId.equals(getLeader(memberIds));
            boolean previouslyElected = elected.getAndSet(electedLeader);

            if (!previouslyElected && electedLeader) {
              LOG.info(String.format("Candidate %s is now leader of group: %s",
                  membership.getMemberPath(), memberIds));

              leader.onElected(new ExceptionalCommand<JoinException>() {
                @Override public void execute() throws JoinException {
                  membership.cancel();
                  abdicated.set(true);
                }
              });
            } else if (!electedLeader) {
              if (previouslyElected) {
                leader.onDefeated();
              }
              LOG.info(String.format(
                  "Candidate %s waiting for the next leader election, current voting: %s",
                  membership.getMemberPath(), memberIds));
            }
          }
        }
      });

    return new Supplier<Boolean>() {
        @Override public Boolean get() {
          return !abdicated.get() && elected.get();
        }
      };
  }

  private String getLeader(Iterable<String> memberIds) {
    return judge.apply(memberIds);
  }
}
