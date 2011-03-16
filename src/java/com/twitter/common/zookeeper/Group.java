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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.twitter.common.base.Command;
import com.twitter.common.base.Commands;
import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.util.BackoffHelper;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.data.ACL;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * This class exposes methods for joining and monitoring distributed groups.  The groups this class
 * monitors are realized as persistent paths in ZooKeeper with ephemeral sequential child nodes for
 * each member of a group.
 *
 * @author John Sirois
 */
public class Group {
  private static final Logger LOG = Logger.getLogger(Partitioner.class.getName());

  private static final Supplier<byte[]> NO_MEMBER_DATA = Suppliers.ofInstance(null);
  private static final String DEFAULT_NODE_NAME_PREFIX = "member_";

  private Predicate<String> nodeNameFilter;

  private final ZooKeeperClient zkClient;
  private final List<ACL> acl;
  private final String path;
  private final String nodeNamePrefix;

  private final BackoffHelper backoffHelper;

  /**
   * @param zkClient the client to use for interactions with ZooKeeper
   * @param acl the ACL to use for creating the persistent group path if it does not already exist
   * @param path the persistent path that represents this group
   * @param nodeNamePrefix Node name prefix that denotes group membership.
   */
  public Group(ZooKeeperClient zkClient, List<ACL> acl, String path, String nodeNamePrefix) {
    this.zkClient = Preconditions.checkNotNull(zkClient);
    this.acl = Preconditions.checkNotNull(acl);
    this.path = MorePreconditions.checkNotBlank(path);
    this.nodeNamePrefix = MorePreconditions.checkNotBlank(nodeNamePrefix);

    final Pattern groupNodeNamePattern = Pattern.compile(
        "^" + Pattern.quote(nodeNamePrefix) + "[0-9]+$");
    nodeNameFilter = new Predicate<String>() {
        @Override public boolean apply(String childNodeName) {
          return groupNodeNamePattern.matcher(childNodeName).matches();
        }
      };

    backoffHelper = new BackoffHelper();
  }

  /**
   * @param zkClient the client to use for interactions with ZooKeeper
   * @param acl the ACL to use for creating the persistent group path if it does not already exist
   * @param path the persistent path that represents this group
   */
  public Group(ZooKeeperClient zkClient, List<ACL> acl, String path) {
    this(zkClient, acl, path, DEFAULT_NODE_NAME_PREFIX);
  }

  public String getMemberPath(String memberId) {
    return path + "/" + MorePreconditions.checkNotBlank(memberId);
  }

  public String getMemberId(String nodePath) {
    MorePreconditions.checkNotBlank(nodePath);
    Preconditions.checkArgument(nodePath.startsWith(path + "/"),
        "Not a member of this group[%s]: %s", path, nodePath);
    return extractMemberId(nodePath);
  }

  private String extractMemberId(String nodePath) {
    String memberId = StringUtils.substringAfterLast(nodePath, "/");
    Preconditions.checkArgument(nodeNameFilter.apply(memberId), "Not a group member: %s", memberId);
    return memberId;
  }

  /**
   * Returns the current list of group member ids by querying ZooKeeper synchronously.
   *
   * @return the ids of all the present members of this group
   * @throws ZooKeeperConnectionException if there was a problem connecting to ZooKeeper
   * @throws KeeperException if there was a problem reading this group's member ids
   * @throws InterruptedException if this thread is interrupted listing the group members
   */
  public Iterable<String> getMemberIds()
      throws ZooKeeperConnectionException, KeeperException, InterruptedException {
    return Iterables.filter(zkClient.get().getChildren(path, false), nodeNameFilter);
  }

  /**
   * Gets the data for one of this groups members by querying ZooKeeper synchronously.
   *
   * @param memberId the id of the member whose data to retrieve
   * @return the data associated with the {@code memberId}
   * @throws ZooKeeperConnectionException if there was a problem connecting to ZooKeeper
   * @throws KeeperException if there was a problem reading this member's data
   * @throws InterruptedException if this thread is interrupted retrieving the member data
   */
  public byte[] getMemberData(String memberId)
      throws ZooKeeperConnectionException, KeeperException, InterruptedException {
    return zkClient.get().getData(getMemberPath(memberId), false, null);
  }

  /**
   * Represents membership in a distributed group.
   */
  public interface Membership {

    /**
     * Returns the persistent ZooKeeper path that represents this group.
     */
    String getGroupPath();

    /**
     * Returns the id (ZooKeeper node name) of this group member.  May change over time if the
     * ZooKeeper session expires.
     */
    String getMemberId();

    /**
     * Returns the full ZooKeeper path to this group member.  May change over time if the
     * ZooKeeper session expires.
     */
    String getMemberPath();

    /**
     * Updates the membership data synchronously using the {@code Supplier<byte[]>} passed to
     * {@link Group#join()}.
     *
     * @return the new membership data
     * @throws UpdateException if there was a problem updating the membership data
     */
    byte[] updateMemberData() throws UpdateException;

    /**
     * Cancels group membership by deleting the associated ZooKeeper member node.
     *
     * @throws JoinException if there is a problem deleting the node
     */
    void cancel() throws JoinException;
  }

  /**
   * Indicates an error joining a group.
   */
  public static class JoinException extends Exception {
    public JoinException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Indicates an error updating a group member's data.
   */
  public static class UpdateException extends Exception {
    public UpdateException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Equivalent to calling {@code join(null, null)}.
   */
  public final Membership join() throws JoinException, InterruptedException {
    return join(NO_MEMBER_DATA, null);
  }

  /**
   * Equivalent to calling {@code join(memberData, null)}.
   */
  public final Membership join(Supplier<byte[]> memberData)
      throws JoinException, InterruptedException {

    return join(memberData, null);
  }

  /**
   * Equivalent to calling {@code join(null, onLoseMembership)}.
   */
  public final Membership join(@Nullable final Command onLoseMembership)
      throws JoinException, InterruptedException {

    return join(NO_MEMBER_DATA, onLoseMembership);
  }

  /**
   * Joins this group and returns the resulting Membership when successful.  Membership will be
   * automatically cancelled when the current jvm process dies; however the returned Membership
   * object can be used to cancel membership earlier.  Unless
   * {@link com.twitter.common.zookeeper.Group.Membership#cancel()} is called the membership will
   * be maintained by re-establishing it silently in the background.
   *
   * <p>Any {@code memberData} given is persisted in the member node in ZooKeeper.  If an
   * {@code onLoseMembership} callback is supplied, it will be notified each time this member loses
   * membership in the group.
   *
   * @param memberData a supplier of the data to store in the member node
   * @param onLoseMembership a callback to notify when membership is lost
   * @return a Membership object with the member details
   * @throws JoinException if there was a problem joining the group
   * @throws InterruptedException if this thread is interrupted awaiting completion of the join
   */
  public final Membership join(Supplier<byte[]> memberData, @Nullable Command onLoseMembership)
      throws JoinException, InterruptedException {

    Preconditions.checkNotNull(memberData);
    ensurePersistentGroupPath();

    final ActiveMembership groupJoiner = new ActiveMembership(memberData, onLoseMembership);
    return backoffHelper.doUntilResult(new ExceptionalSupplier<Membership, JoinException>() {
      @Override public Membership get() throws JoinException {
        try {
          return groupJoiner.join();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new JoinException("Interrupted trying to join group at path: " + path, e);
        } catch (ZooKeeperConnectionException e) {
          LOG.log(Level.WARNING, "Temporary error trying to join group at path: " + path, e);
          return null;
        } catch (KeeperException e) {
          if (zkClient.shouldRetry(e)) {
            LOG.log(Level.WARNING, "Temporary error trying to join group at path: " + path, e);
            return null;
          } else {
            throw new JoinException("Problem joining partition group at path: " + path, e);
          }
        }
      }
    });
  }

  private void ensurePersistentGroupPath() throws JoinException, InterruptedException {
    backoffHelper.doUntilSuccess(new ExceptionalSupplier<Boolean, JoinException>() {
      @Override public Boolean get() throws JoinException {
        try {
          ZooKeeperUtils.ensurePath(zkClient, acl, path);
          return true;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new JoinException("Interrupted trying to ensure group at path: " + path, e);
        } catch (ZooKeeperConnectionException e) {
          LOG.log(Level.WARNING, "Problem connecting to ZooKeeper, retrying", e);
          return false;
        } catch (KeeperException e) {
          if (zkClient.shouldRetry(e)) {
            LOG.log(Level.WARNING, "Temporary error ensuring path: " + path, e);
            return false;
          } else {
            throw new JoinException("Problem ensuring group at path: " + path, e);
          }
        }
      }
    });
  }

  private class ActiveMembership implements Membership {
    private final Supplier<byte[]> memberData;
    private final Command onLoseMembership;
    private String nodePath;
    private String memberId;
    private volatile boolean cancelled;
    private byte[] membershipData;

    public ActiveMembership(Supplier<byte[]> memberData, @Nullable Command onLoseMembership) {
      this.memberData = memberData;
      this.onLoseMembership = (onLoseMembership == null) ? Commands.NOOP : onLoseMembership;
    }

    @Override
    public String getGroupPath() {
      return path;
    }

    @Override
    public synchronized String getMemberId() {
      return memberId;
    }

    @Override
    public synchronized String getMemberPath() {
      return nodePath;
    }

    @Override
    public synchronized byte[] updateMemberData() throws UpdateException {
      byte[] membershipData = memberData.get();
      if (!ArrayUtils.isEquals(this.membershipData, membershipData)) {
        try {
          zkClient.get().setData(nodePath, membershipData, ZooKeeperUtils.ANY_VERSION);
          this.membershipData = membershipData;
        } catch (KeeperException e) {
          throw new UpdateException("Problem updating membership data.", e);
        } catch (InterruptedException e) {
          throw new UpdateException("Interrupted attempting to update membership data.", e);
        } catch (ZooKeeperConnectionException e) {
          throw new UpdateException(
              "Could not connect to the ZooKeeper cluster to update membership data.", e);
        }
      }
      return membershipData;
    }

    @Override
    public synchronized void cancel() throws JoinException {
      if (!cancelled) {
        try {
          backoffHelper.doUntilSuccess(new ExceptionalSupplier<Boolean, JoinException>() {
            @Override public Boolean get() throws JoinException {
              try {
                zkClient.get().delete(nodePath, ZooKeeperUtils.ANY_VERSION);
                return true;
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new JoinException("Interrupted trying to cancel membership: " + nodePath, e);
              } catch (ZooKeeperConnectionException e) {
                LOG.log(Level.WARNING, "Problem connecting to ZooKeeper, retrying", e);
                return false;
              } catch (NoNodeException e) {
                LOG.info("Membership already cancelled, node at path: " + nodePath +
                         " has been deleted");
                return true;
              } catch (KeeperException e) {
                if (zkClient.shouldRetry(e)) {
                  LOG.log(Level.WARNING, "Temporary error cancelling membership: " + nodePath, e);
                  return false;
                } else {
                  throw new JoinException("Problem cancelling membership: " + nodePath, e);
                }
              }
            }
          });
          cancelled = true; // Prevent auto-re-join logic from undoing this cancel.
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new JoinException("Problem cancelling membership: " + nodePath, e);
        }
      }
    }

    private class CancelledException extends IllegalStateException { /* marker */ }

    synchronized Membership join()
        throws ZooKeeperConnectionException, InterruptedException, KeeperException {

      if (cancelled) {
        throw new CancelledException();
      }

      if (nodePath == null) {
        // Re-join if our ephemeral node goes away due to session expiry - only needs to be
        // registered once.
        zkClient.registerExpirationHandler(new Command() {
          @Override public void execute() {
            tryJoin();
          }
        });
      }

      byte[] membershipData = memberData.get();
      nodePath = zkClient.get().create(path + "/" + nodeNamePrefix, membershipData, acl,
          CreateMode.EPHEMERAL_SEQUENTIAL);
      memberId = extractMemberId(nodePath);
      this.membershipData = membershipData;

      // Re-join if our ephemeral node goes away due to maliciousness.
      zkClient.get().exists(nodePath, new Watcher() {
        @Override public void process(WatchedEvent event) {
          if (event.getType() == EventType.NodeDeleted) {
            tryJoin();
          }
        }
      });

      return this;
    }

    private final ExceptionalSupplier<Boolean, InterruptedException> tryJoin =
        new ExceptionalSupplier<Boolean, InterruptedException>() {
          @Override public Boolean get() throws InterruptedException {
            try {
              join();
              return true;
            } catch (CancelledException e) {
              // Lost a cancel race - that's ok.
              return true;
            } catch (ZooKeeperConnectionException e) {
              LOG.log(Level.WARNING, "Problem connecting to ZooKeeper, retrying", e);
              return false;
            } catch (KeeperException e) {
              if (zkClient.shouldRetry(e)) {
                LOG.log(Level.WARNING, "Temporary error re-joining group: " + path, e);
                return false;
              } else {
                throw new IllegalStateException("Permanent problem re-joining group: " + path, e);
              }
            }
          }
        };

    private synchronized void tryJoin() {
      onLoseMembership.execute();
      try {
        backoffHelper.doUntilSuccess(tryJoin);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(
            String.format("Interrupted while trying to re-join group: %s, giving up", path), e);
      }
    }
  }

  /**
   * An interface to an object that listens for changes to a group's membership.
   */
  public interface GroupChangeListener {

    /**
     * Called whenever group membership changes with the new list of member ids.
     *
     * @param memberIds the current member ids
     */
    void onGroupChange(Iterable<String> memberIds);
  }

  /**
   * Indicates an error watching a group.
   */
  public static class WatchException extends Exception {
    public WatchException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Watches this group for the lifetime of this jvm process.  This method will block until the
   * current group members are available, notify the {@code groupChangeListener} and then return.
   * All further changes to the group membership will cause notifications on a background thread.
   *
   * @param groupChangeListener the listener to notify of group membership change events
   * @throws WatchException if there is a problem generating the 1st group membership list
   * @throws InterruptedException if interrupted waiting to gather the 1st group membership list
   */
  public final void watch(final GroupChangeListener groupChangeListener)
      throws WatchException, InterruptedException {
    Preconditions.checkNotNull(groupChangeListener);

    try {
      ensurePersistentGroupPath();
    } catch (JoinException e) {
      throw new WatchException("Failed to create group path: " + path, e);
    }

    final GroupMonitor groupMonitor = new GroupMonitor(groupChangeListener);
    backoffHelper.doUntilSuccess(new ExceptionalSupplier<Boolean, WatchException>() {
      @Override public Boolean get() throws WatchException {
        try {
          groupMonitor.watchGroup();
          return true;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new WatchException("Interrupted trying to watch group at path: " + path, e);
        } catch (ZooKeeperConnectionException e) {
          LOG.log(Level.WARNING, "Temporary error trying to watch group at path: " + path, e);
          return null;
        } catch (KeeperException e) {
          if (zkClient.shouldRetry(e)) {
            LOG.log(Level.WARNING, "Temporary error trying to watch group at path: " + path, e);
            return null;
          } else {
            throw new WatchException("Problem trying to watch group at path: " + path, e);
          }
        }
      }
    });
  }

  /**
   * Helps continuously monitor a group for membership changes.
   */
  private class GroupMonitor {
    private final GroupChangeListener groupChangeListener;
    private Set<String> members;

    GroupMonitor(GroupChangeListener groupChangeListener) {
      this.groupChangeListener = groupChangeListener;
    }

    private final Watcher groupWatcher = new Watcher() {
      @Override public final void process(WatchedEvent event) {
        if (event.getType() == EventType.NodeChildrenChanged) {
          tryWatchGroup();
        }
      }
    };

    private final ExceptionalSupplier<Boolean, InterruptedException> tryWatchGroup =
        new ExceptionalSupplier<Boolean, InterruptedException>() {
          @Override public Boolean get() throws InterruptedException {
            try {
              watchGroup();
              return true;
            } catch (ZooKeeperConnectionException e) {
              LOG.log(Level.WARNING, "Problem connecting to ZooKeeper, retrying", e);
              return false;
            } catch (KeeperException e) {
              if (zkClient.shouldRetry(e)) {
                LOG.log(Level.WARNING, "Temporary error re-watching group: " + path, e);
                return false;
              } else {
                throw new IllegalStateException("Permanent problem re-watching group: " + path, e);
              }
            }
          }
        };

    private void tryWatchGroup() {
      try {
        backoffHelper.doUntilSuccess(tryWatchGroup);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(
            String.format("Interrupted while trying to re-watch group: %s, giving up", path), e);
      }
    }

    private void watchGroup()
        throws ZooKeeperConnectionException, InterruptedException, KeeperException {

      List<String> children = zkClient.get().getChildren(path, groupWatcher);
      setMembers(Iterables.filter(children, nodeNameFilter));
    }

    synchronized void setMembers(Iterable<String> members) {
      if (this.members == null) {
        // Reset our watch on the group if session expires - only needs to be registered once.
        zkClient.registerExpirationHandler(new Command() {
          @Override public void execute() {
            tryWatchGroup();
          }
        });
      }

      Set<String> membership = ImmutableSet.copyOf(members);
      if (!membership.equals(this.members)) {
        groupChangeListener.onGroupChange(members);
        this.members = membership;
      }
    }
  }

  @Override
  public String toString() {
    return "Group " + path;
  }
}
