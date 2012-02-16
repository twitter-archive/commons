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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.Ordering;
import com.twitter.common.zookeeper.Group.GroupChangeListener;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.Group.Membership;
import com.twitter.common.zookeeper.Group.UpdateException;
import com.twitter.common.zookeeper.Group.WatchException;
import org.apache.zookeeper.data.ACL;

import javax.annotation.Nullable;
import java.util.List;
import java.util.logging.Logger;

/**
 * A distributed mechanism for eventually arriving at an evenly partitioned space of long values.
 * A typical usage would have a client on each of several hosts joining a logical partition (a
 * "partition group") that represents some shared work.  Clients could then process a subset of a
 * full body of work by testing any given item of work with their partition filter.
 *
 * <p>Note that clients must be able to tolerate periods of duplicate processing by more than 1
 * partition as explained in {@link #join()}.
 *
 * @author John Sirois
 */
public class Partitioner {

  private static final Logger LOG = Logger.getLogger(Partitioner.class.getName());

  private volatile int groupSize;
  private volatile int groupIndex;
  private final Group group;

  /**
   * Constructs a representation of a partition group but does not join it.  Note that the partition
   * group path will be created as a persistent zookeeper path if it does not already exist.
   *
   * @param zkClient a client to use for joining the partition group and watching its membership
   * @param acl the acl for this partition group
   * @param path a zookeeper path that represents the partition group
   */
  public Partitioner(ZooKeeperClient zkClient, List<ACL> acl, String path) {
    group = new Group(zkClient, acl, path);
  }

  @VisibleForTesting
  int getGroupSize() {
    return groupSize;
  }

  /**
   * Represents a slice of a partition group.  The partition is dynamic and will adjust its size as
   * members join and leave its partition group.
   */
  public abstract static class Partition implements Predicate<Long>, Membership {

    /**
     * Returns {@code true} if the given {@code value} is a member of this partition at this time.
     */
    public abstract boolean isMember(long value);

    /**
     * Gets number of members in the group at this time.
     *
     * @return number of members in the ZK group at this time.
     */
    public abstract int getNumPartitions();

    /**
     * Evaluates partition membership based on the given {@code value}'s hash code.  If the value
     * is null it is never a member of a partition.
     */
    boolean isMember(Object value) {
      return (value != null) && isMember(value.hashCode());
    }

    /**
     * Equivalent to {@link #isMember(long)} for all non-null values; however incurs unboxing
     * overhead.
     */
    @Override
    public boolean apply(@Nullable Long input) {
      return (input != null) && isMember(input);
    }
  }

  /**
   * Attempts to join the partition group and claim a slice.  When successful, a predicate is
   * returned that can be used to test whether or not an item belongs to this partition.  The
   * predicate is dynamic such that as the group is further partitioned or partitions merge the
   * predicate will claim a narrower or wider swath of the partition space respectively.  Partition
   * creation and merging is not instantaneous and clients should expect independent partitions to
   * claim ownership of some items when partition membership is in flux.  It is only in the steady
   * state that a client should expect independent partitions to divide the partition space evenly
   * and without overlap.
   *
   * <p>TODO(John Sirois): consider adding a version with a global timeout for the join operation.
   *
   * @return the partition representing the slice of the partition group this member can claim
   * @throws JoinException if there was a problem joining the partition group
   * @throws InterruptedException if interrupted while waiting to join the partition group
   */
  public final Partition join() throws JoinException, InterruptedException {
    final Membership membership = group.join();
    try {
      group.watch(createGroupChangeListener(membership));
    } catch (WatchException e) {
      membership.cancel();
      throw new JoinException("Problem establishing watch on group after joining it", e);
    }
    return new Partition() {
      @Override public boolean isMember(long value) {
        return (value % groupSize) == groupIndex;
      }

      @Override public int getNumPartitions() {
        return groupSize;
      }

      @Override public String getGroupPath() {
        return membership.getGroupPath();
      }

      @Override public String getMemberId() {
        return membership.getMemberId();
      }

      @Override public String getMemberPath() {
        return membership.getMemberPath();
      }

      @Override public byte[] updateMemberData() throws UpdateException {
        return membership.updateMemberData();
      }

      @Override public void cancel() throws JoinException {
        membership.cancel();
      }
    };
  }

  @VisibleForTesting GroupChangeListener createGroupChangeListener(final Membership membership) {
    return new GroupChangeListener() {
      @Override public void onGroupChange(Iterable<String> memberIds) {
        List<String> members = Ordering.natural().sortedCopy(memberIds);
        int newSize = members.size();
        int newIndex = members.indexOf(membership.getMemberId());

        LOG.info(String.format("Rebuilding group %s:%s [%d:%d]->[%d:%d]",
            membership.getGroupPath(), members, groupSize, groupIndex, newSize, newIndex));

        groupSize = newSize;
        groupIndex = newIndex;
      }
    };
  }
}
