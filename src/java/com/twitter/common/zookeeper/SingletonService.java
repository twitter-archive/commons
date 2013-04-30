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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;

import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.zookeeper.Candidate.Leader;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.ServerSet.EndpointStatus;
import com.twitter.common.zookeeper.ServerSet.UpdateException;
import com.twitter.thrift.Status;

/**
 * A service that uses master election to only allow a single instance of the server to join
 * the {@link ServerSet} at a time.
 */
public class SingletonService {
  private static final Logger LOG = Logger.getLogger(SingletonService.class.getName());

  @VisibleForTesting
  static final String LEADER_ELECT_NODE_PREFIX = "singleton_candidate_";

  /**
   * Creates a candidate that can be combined with an existing server set to form a singleton
   * service using {@link #SingletonService(ServerSet, Candidate)}.
   *
   * @param zkClient The ZooKeeper client to use.
   * @param servicePath The path where service nodes live.
   * @param acl The acl to apply to newly created candidate nodes and serverset nodes.
   * @return A candidate that can be housed with a standard server set under a single zk path.
   */
  public static Candidate createSingletonCandidate(
      ZooKeeperClient zkClient,
      String servicePath,
      Iterable<ACL> acl) {

    return new CandidateImpl(new Group(zkClient, acl, servicePath, LEADER_ELECT_NODE_PREFIX));
  }

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
    this(
        new ServerSetImpl(zkClient, new Group(zkClient, acl, servicePath)),
        createSingletonCandidate(zkClient, servicePath, acl));
  }

  /**
   * Creates a new singleton service that uses the supplied candidate to vie for leadership and then
   * advertises itself in the given server set once elected.
   *
   * @param serverSet The server set to advertise in on election.
   * @param candidate The candidacy to use to vie for election.
   */
  public SingletonService(ServerSet serverSet, Candidate candidate) {
    this.serverSet = Preconditions.checkNotNull(serverSet);
    this.candidate = Preconditions.checkNotNull(candidate);
  }

  /**
   * Attempts to lead the singleton service.
   *
   * @param endpoint The primary endpoint to register as a leader candidate in the service.
   * @param additionalEndpoints Additional endpoints that are available on the host.
   * @param status deprecated, will be ignored entirely
   * @param listener Handler to call when the candidate is elected or defeated.
   * @throws Group.WatchException If there was a problem watching the ZooKeeper group.
   * @throws Group.JoinException If there was a problem joining the ZooKeeper group.
   * @throws InterruptedException If the thread watching/joining the group was interrupted.
   * @deprecated The status field is deprecated. Please use
   *            {@link #lead(InetSocketAddress, Map, LeadershipListener)}
   */
  @Deprecated
  public void lead(final InetSocketAddress endpoint,
                   final Map<String, InetSocketAddress> additionalEndpoints,
                   final Status status,
                   final LeadershipListener listener)
                   throws Group.WatchException, Group.JoinException, InterruptedException {

    if (status != Status.ALIVE) {
      LOG.severe("******************************************************************************");
      LOG.severe("WARNING: MUTABLE STATUS FIELDS ARE NO LONGER SUPPORTED.");
      LOG.severe("JOINING WITH STATUS ALIVE EVEN THOUGH YOU SPECIFIED " + status);
      LOG.severe("******************************************************************************");
    } else {
      LOG.warning("******************************************************************************");
      LOG.warning("WARNING: MUTABLE STATUS FIELDS ARE NO LONGER SUPPORTED.");
      LOG.warning("Please use SingletonService.lead(InetSocketAddress, Map, LeadershipListener)");
      LOG.warning("******************************************************************************");
    }

    lead(endpoint, additionalEndpoints, listener);
  }

  /**
   * Attempts to lead the singleton service.
   *
   * @param endpoint The primary endpoint to register as a leader candidate in the service.
   * @param additionalEndpoints Additional endpoints that are available on the host.
   * @param listener Handler to call when the candidate is elected or defeated.
   * @throws Group.WatchException If there was a problem watching the ZooKeeper group.
   * @throws Group.JoinException If there was a problem joining the ZooKeeper group.
   * @throws InterruptedException If the thread watching/joining the group was interrupted.
   */
  public void lead(final InetSocketAddress endpoint,
                   final Map<String, InetSocketAddress> additionalEndpoints,
                   final LeadershipListener listener)
                   throws Group.WatchException, Group.JoinException, InterruptedException {

    Preconditions.checkNotNull(listener);

    candidate.offerLeadership(new Leader() {
      private EndpointStatus endpointStatus = null;
      @Override public void onElected(final ExceptionalCommand<JoinException> abdicate) {
        listener.onLeading(new LeaderControl() {
          EndpointStatus endpointStatus = null;
          final AtomicBoolean left = new AtomicBoolean(false);

          // Methods are synchronized to prevent simultaneous invocations.
          @Override public synchronized void advertise()
              throws JoinException, InterruptedException {

            Preconditions.checkState(!left.get(), "Cannot advertise after leaving.");
            Preconditions.checkState(endpointStatus == null, "Cannot advertise more than once.");
            endpointStatus = serverSet.join(endpoint, additionalEndpoints);
          }

          @Override public synchronized void leave() throws UpdateException, JoinException {
            Preconditions.checkState(left.compareAndSet(false, true),
                "Cannot leave more than once.");
            if (endpointStatus != null) {
              endpointStatus.leave();
            }
            abdicate.execute();
          }
        });
      }

      @Override public void onDefeated() {
        listener.onDefeated(endpointStatus);
      }
    });
  }

  /**
   * A listener to be notified of changes in the leadership status.
   * Implementers should be careful to avoid blocking operations in these callbacks.
   */
  public interface LeadershipListener {

    /**
     * Notifies the listener that is is current leader.
     *
     * @param control A controller handle to advertise and/or leave advertised presence.
     */
    public void onLeading(LeaderControl control);

    /**
     * Notifies the listener that it is no longer leader.  The leader should take this opportunity
     * to remove its advertisement gracefully.
     *
     * @param status A handle on the endpoint status for the advertised leader.
     */
    public void onDefeated(@Nullable EndpointStatus status);
  }

  /**
   * A leadership listener that decorates another listener by automatically defeating a
   * leader that has dropped its connection to ZooKeeper.
   * Note that the decision to use this over session-based mutual exclusion should not be taken
   * lightly.  Any momentary connection loss due to a flaky network or a ZooKeeper server process
   * exit will cause a leader to abort.
   */
  public static class DefeatOnDisconnectLeader implements LeadershipListener {

    private final LeadershipListener wrapped;
    private Optional<LeaderControl> maybeControl = Optional.absent();

    /**
     * Creates a new leadership listener that will delegate calls to the wrapped listener, and
     * invoke {@link #onDefeated(EndpointStatus)} if a ZooKeeper disconnect is observed while
     * leading.
     *
     * @param zkClient The ZooKeeper client to watch for disconnect events.
     * @param wrapped The leadership listener to wrap.
     */
    public DefeatOnDisconnectLeader(ZooKeeperClient zkClient, LeadershipListener wrapped) {
      this.wrapped = Preconditions.checkNotNull(wrapped);

      zkClient.register(new Watcher() {
        @Override public void process(WatchedEvent event) {
          if ((event.getType() == EventType.None)
              && (event.getState() == KeeperState.Disconnected)) {
            disconnected();
          }
        }
      });
    }

    private synchronized void disconnected() {
      if (maybeControl.isPresent()) {
        LOG.warning("Disconnected from ZooKeeper while leading, committing suicide.");
        try {
          wrapped.onDefeated(null);
          maybeControl.get().leave();
        } catch (UpdateException e) {
          LOG.log(Level.WARNING, "Failed to leave singleton service: " + e, e);
        } catch (JoinException e) {
          LOG.log(Level.WARNING, "Failed to leave singleton service: " + e, e);
        } finally {
          setControl(null);
        }
      } else {
        LOG.info("Disconnected from ZooKeeper, but that's fine because I'm not the leader.");
      }
    }

    private synchronized void setControl(@Nullable LeaderControl control) {
      this.maybeControl = Optional.fromNullable(control);
    }

    @Override public void onLeading(final LeaderControl control) {
      setControl(control);
      wrapped.onLeading(new LeaderControl() {
        @Override public void advertise() throws JoinException, InterruptedException {
          control.advertise();
        }

        @Override public void leave() throws UpdateException, JoinException {
          setControl(null);
          control.leave();
        }
      });
    }

    @Override public void onDefeated(@Nullable EndpointStatus status) {
      setControl(null);
      wrapped.onDefeated(status);
    }
  }

  /**
   * A controller for the state of the leader.  This will be provided to the leader upon election,
   * which allows the leader to decide when to advertise in the underlying {@link ServerSet} and
   * terminate leadership at will.
   */
  public interface LeaderControl {

    /**
     * Advertises the leader's server presence to clients.
     *
     * @throws JoinException If there was an error advertising.
     * @throws InterruptedException If interrupted while advertising.
     */
    void advertise() throws JoinException, InterruptedException;

    /**
     * Leaves candidacy for leadership, removing advertised server presence if applicable.
     *
     * @throws UpdateException If the leader's status could not be updated.
     * @throws JoinException If there was an error abdicating from leader election.
     */
    void leave() throws UpdateException, JoinException;
  }
}
