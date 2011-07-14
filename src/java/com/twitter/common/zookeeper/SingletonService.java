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

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;

import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.zookeeper.Candidate.Leader;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.ServerSet.EndpointStatus;
import com.twitter.thrift.Status;

/**
 * A service that uses master election to only allow a single instance of the server to join
 * the {@link ServerSet} at a time.
 *
 * @author William Farner
 */
public class SingletonService {

  private static final Logger LOG = Logger.getLogger(SingletonService.class.getName());
  private static final String LEADER_ELECT_NODE_PREFIX = "singleton_candidate_";

  private final ServerSet serverSet;
  private final Candidate candidate;

  /**
   * Equivalent to {@link #SingletonService(ZooKeeperClient, String, Iterable)} with a default
   * wide open {@code acl} ({@link ZooDefs.Ids#OPEN_ACL_UNSAFE}).
   */
  public SingletonService(ZooKeeperClient zkClient, String servicePath) {
    this(zkClient, servicePath, ZooDefs.Ids.OPEN_ACL_UNSAFE);
  }

  /**
   * Creates a new singleton service, identified by {@code servicePath}.  All nodes related to the
   * service (for both leader election and service registration) will live under the path and each
   * node will be created with the supplied {@code acl}. Internally, two ZooKeeper {@code Group}s
   * are used to manage a singleton service - one for leader election, and another for the
   * {@code ServerSet} where the leader's endpoints are registered.  Leadership election should
   * guarantee that at most one instance will ever exist in the ServerSet at once.
   *
   * @param zkClient The ZooKeeper client to use.
   * @param servicePath The path where service nodes live.
   * @param acl The acl to apply to newly created candidate nodes and serverset nodes.
   */
  public SingletonService(ZooKeeperClient zkClient, String servicePath, Iterable<ACL> acl) {
    this(new ServerSetImpl(zkClient, new Group(zkClient, acl, servicePath)),
        new CandidateImpl(new Group(zkClient, acl, servicePath, LEADER_ELECT_NODE_PREFIX)));
  }

  @VisibleForTesting
  SingletonService(ServerSet serverSet, Candidate candidate) {
    this.serverSet = Preconditions.checkNotNull(serverSet);
    this.candidate = Preconditions.checkNotNull(candidate);
  }

  /**
   * Attempts to lead the singleton service.
   *
   * @param endpoint The primary endpoint to register as a leader candidate in the service.
   * @param additionalEndpoints Additional endpoints that are available on the host.
   * @param status Current status of the candidate.
   * @param listener Handler to call when the candidate is elected or defeated.
   * @throws Group.WatchException If there was a problem watching the ZooKeeper group.
   * @throws Group.JoinException If there was a problem joining the ZooKeeper group.
   * @throws InterruptedException If the thread watching/joining the group was interrupted.
   */
  public void lead(final InetSocketAddress endpoint,
      final Map<String, InetSocketAddress> additionalEndpoints,
      final Status status, final LeadershipListener listener)
      throws Group.WatchException, Group.JoinException, InterruptedException {
    Preconditions.checkNotNull(listener);

    candidate.offerLeadership(new Leader() {
      private EndpointStatus endpointStatus = null;
      @Override public void onElected(ExceptionalCommand<JoinException> abdicate) {
        try {
          endpointStatus = serverSet.join(endpoint, additionalEndpoints, status);
          listener.onLeading(endpointStatus);
        } catch (Group.JoinException e) {
          LOG.log(Level.SEVERE, "Failed to join group.", e);
        } catch (InterruptedException e) {
          LOG.log(Level.SEVERE, "Interrupted while joining group.", e);
          Thread.currentThread().interrupt();
        }
      }

      @Override public void onDefeated() {
        listener.onDefeated(endpointStatus);
      }
    });
  }

  public static interface LeadershipListener {
    public void onLeading(EndpointStatus status);

    public void onDefeated(@Nullable EndpointStatus status);
  }
}
