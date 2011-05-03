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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ComputationException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;

import com.twitter.common.base.Command;
import com.twitter.common.base.Function;
import com.twitter.common.base.Supplier;
import com.twitter.common.io.Codec;
import com.twitter.common.io.ThriftCodec;
import com.twitter.common.util.BackoffHelper;
import com.twitter.common.zookeeper.Group.GroupChangeListener;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.Group.Membership;
import com.twitter.common.zookeeper.Group.WatchException;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;
import com.twitter.thrift.Endpoint;
import com.twitter.thrift.ServiceInstance;
import com.twitter.thrift.Status;

/**
 * Implementation of {@link ServerSet}.
 *
 * @author John Sirois
 */
public class ServerSetImpl implements ServerSet {
  private static final Logger LOG = Logger.getLogger(ServerSetImpl.class.getName());

  // Binary protocol is used here since it is currently (5/18/10) compatible all thrift languages.
  private static final Codec<ServiceInstance> THRIFT_BINARY_PROTOCOL_CODEC =
      new ThriftCodec<ServiceInstance>(ServiceInstance.class, ThriftCodec.BINARY_PROTOCOL);

  private final ZooKeeperClient zkClient;
  private final Group group;
  private final Codec<ServiceInstance> codec;
  private final BackoffHelper backoffHelper;

  /**
   * Creates a new ServerSet using open ZooKeeper node ACLs.
   *
   * @param zkClient the client to use for interactions with ZooKeeper
   * @param path the name-service path of the service to connect to
   */
  public ServerSetImpl(ZooKeeperClient zkClient, String path) {
    this(zkClient, ZooDefs.Ids.OPEN_ACL_UNSAFE, path);
  }

  /**
   * Creates a new ServerSet for the given service {@code path}.
   *
   * @param zkClient the client to use for interactions with ZooKeeper
   * @param acl the ACL to use for creating the persistent group path if it does not already exist
   * @param path the name-service path of the service to connect to
   */
  public ServerSetImpl(ZooKeeperClient zkClient, List<ACL> acl, String path) {
    this(zkClient, new Group(zkClient, acl, path), THRIFT_BINARY_PROTOCOL_CODEC);
  }

  /**
   * Creates a new ServerSet using the given service {@code group}.
   *
   * @param zkClient the client to use for interactions with ZooKeeper
   * @param group the server group
   */
  public ServerSetImpl(ZooKeeperClient zkClient, Group group) {
    this(zkClient, group, THRIFT_BINARY_PROTOCOL_CODEC);
  }

  /**
   * Creates a new ServerSet using the given service {@code group} and a custom {@code codec}.
   *
   * @param zkClient the client to use for interactions with ZooKeeper
   * @param group the server group
   * @param codec a codec to use for serializing and de-serializing the ServiceInstance data to and
   *     from a byte array
   */
  public ServerSetImpl(ZooKeeperClient zkClient, Group group, Codec<ServiceInstance> codec) {
    this.zkClient = Preconditions.checkNotNull(zkClient);
    this.group = Preconditions.checkNotNull(group);
    this.codec = codec;

    // TODO(John Sirois): Inject the helper so that backoff strategy can be configurable.
    backoffHelper = new BackoffHelper();
  }

  @VisibleForTesting
  ZooKeeperClient getZkClient() {
    return zkClient;
  }

  @Override
  public EndpointStatus join(InetSocketAddress endpoint,
      Map<String, InetSocketAddress> additionalEndpoints, Status status)
      throws JoinException, InterruptedException {
    Preconditions.checkNotNull(endpoint);
    Preconditions.checkNotNull(additionalEndpoints);
    Preconditions.checkNotNull(status);

    final MemberStatus memberStatus = new MemberStatus(endpoint, additionalEndpoints, status);
    Supplier<byte[]> serviceInstanceSupplier = new Supplier<byte[]>() {
      @Override public byte[] get() {
        return memberStatus.serializeServiceInstance();
      }
    };
    final Membership membership = group.join(serviceInstanceSupplier);

    return new EndpointStatus() {
      @Override public void update(Status status) throws UpdateException {
        Preconditions.checkNotNull(status);
        memberStatus.updateStatus(membership, status);
      }
    };
  }

  @Override
  public void monitor(final HostChangeMonitor<ServiceInstance> monitor) throws MonitorException {
    ServerSetWatcher serverSetWatcher = new ServerSetWatcher(zkClient, monitor);
    try {
      serverSetWatcher.watch();
    } catch (WatchException e) {
      throw new MonitorException("ZooKeeper watch failed.", e);
    } catch (InterruptedException e) {
      throw new MonitorException("Interrupted while watching ZooKeeper.", e);
    }
  }

  private class MemberStatus {
    private final InetSocketAddress endpoint;
    private final Map<String, InetSocketAddress> additionalEndpoints;
    private volatile Status status;

    private MemberStatus(InetSocketAddress endpoint,
        Map<String, InetSocketAddress> additionalEndpoints, Status status) {

      this.endpoint = endpoint;
      this.additionalEndpoints = additionalEndpoints;
      this.status = status;
    }

    synchronized void updateStatus(Membership membership, Status status) throws UpdateException {
      if (this.status != status) {
        this.status = status;
        if (Status.DEAD == status) {
          try {
            membership.cancel();
          } catch (JoinException e) {
            throw new UpdateException(
                "Failed to auto-cancel group membership on transition to DEAD status", e);
          }
        } else {
          try {
            membership.updateMemberData();
          } catch (Group.UpdateException e) {
            throw new UpdateException(
                "Failed to update service data for: " + membership.getMemberPath(), e);
          }
        }
      }
    }

    byte[] serializeServiceInstance() {
      ServiceInstance serviceInstance =
          new ServiceInstance(toEndpoint(endpoint),
              Maps.transformValues(additionalEndpoints, TO_ENDPOINT), status);
      LOG.info("updating endpoint data to:\n\t" + serviceInstance);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try {
        codec.serialize(serviceInstance, output);
      } catch (IOException e) {
        throw new IllegalStateException("Unexpected problem serializing thrift struct: " +
                                        serviceInstance + " to a byte[]", e);
      }
      return output.toByteArray();
    }
  }

  private static final Function<InetSocketAddress, Endpoint> TO_ENDPOINT =
      new Function<InetSocketAddress, Endpoint>() {
        @Override public Endpoint apply(InetSocketAddress address) {
          return toEndpoint(address);
        }
      };

  private static Endpoint toEndpoint(InetSocketAddress address) {
    return new Endpoint(address.getHostName(), address.getPort());
  }

  private static class ServiceInstanceFetchException extends RuntimeException {
    ServiceInstanceFetchException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static class ServiceInstanceDeletedException extends RuntimeException {
    ServiceInstanceDeletedException(String path) {
      super(path);
    }
  }

  private class ServerSetWatcher {
    private final ZooKeeperClient zkClient;
    private final HostChangeMonitor<ServiceInstance> monitor;
    private ImmutableSet<ServiceInstance> serverSet;

    ServerSetWatcher(ZooKeeperClient zkClient, HostChangeMonitor<ServiceInstance> monitor) {
      this.zkClient = zkClient;
      this.monitor = monitor;
    }

    public void watch() throws WatchException, InterruptedException {
      zkClient.registerExpirationHandler(new Command() {
        @Override public void execute() throws RuntimeException {
          // Servers may have changed Status while we were disconnected from ZooKeeper, check and
          // re-register our node watches.
          rebuildServerSet();
        }
      });

      group.watch(new GroupChangeListener() {
        @Override public void onGroupChange(Iterable<String> memberIds) {
          notifyGroupChange(memberIds);
        }
      });
    }

    private Watcher serviceInstanceWatcher = new Watcher() {
      @Override public void process(WatchedEvent event) {
        if (event.getState() == KeeperState.SyncConnected) {
          switch (event.getType()) {
            case None:
              // Ignore re-connects that happen while we're watching
              break;
            case NodeDeleted:
              // Ignore deletes since these trigger a group change through the group node watch.
              break;
            case NodeDataChanged:
              notifyNodeChange(event.getPath());
              break;
            default:
              LOG.severe("Unexpected event watching service node: " + event);
          }
        }
      }
    };

    private ServiceInstance getServiceInstance(final String nodePath) {
      try {
        return backoffHelper.doUntilResult(new Supplier<ServiceInstance>() {
          @Override public ServiceInstance get() {
            try {
              byte[] data = zkClient.get().getData(nodePath, serviceInstanceWatcher, null);
              return codec.deserialize(new ByteArrayInputStream(data));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new ServiceInstanceFetchException(
                  "Interrupted updating service data for: " + nodePath, e);
            } catch (ZooKeeperConnectionException e) {
              LOG.log(Level.WARNING,
                  "Temporary error trying to updating service data for: " + nodePath, e);
              return null;
            } catch (NoNodeException e) {
              invalidateNodePath(nodePath);
              throw new ServiceInstanceDeletedException(nodePath);
            } catch (KeeperException e) {
              if (zkClient.shouldRetry(e)) {
                LOG.log(Level.WARNING,
                    "Temporary error trying to update service data for: " + nodePath, e);
                return null;
              } else {
                throw new ServiceInstanceFetchException(
                    "Failed to update service data for: " + nodePath, e);
              }
            } catch (IOException e) {
              throw new ServiceInstanceFetchException(
                  "Failed to deserialize the ServiceInstance data for: " + nodePath, e);
            }
          }
        });
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ServiceInstanceFetchException(
            "Interrupted trying to update service data for: " + nodePath, e);
      }
    }

    private final Map<String, ServiceInstance> servicesByMemberId = new MapMaker()
        .makeComputingMap(new Function<String, ServiceInstance>() {
          @Override public ServiceInstance apply(String memberId) {
            return getServiceInstance(group.getMemberPath(memberId));
          }
        });

    private void rebuildServerSet() {
      Set<String> memberIds = ImmutableSet.copyOf(servicesByMemberId.keySet());
      servicesByMemberId.clear();
      notifyGroupChange(memberIds);
    }

    private void notifyNodeChange(String changedPath) {
      // Invalidate the associated ServiceInstance to trigger a fetch on group notify.
      String memberId = invalidateNodePath(changedPath);
      notifyGroupChange(Iterables.concat(servicesByMemberId.keySet(), ImmutableList.of(memberId)));
    }

    private String invalidateNodePath(String deletedPath) {
      String memberId = group.getMemberId(deletedPath);
      servicesByMemberId.remove(memberId);
      return memberId;
    }

    private synchronized void notifyGroupChange(Iterable<String> memberIds) {
      Set<String> currentMemberIds = ImmutableSet.copyOf(memberIds);
      Set<String> oldMemberIds = servicesByMemberId.keySet();

      // Ignore no-op state changes except for the 1st when we've seen no group yet.
      if ((serverSet == null) || !currentMemberIds.equals(oldMemberIds)) {
        SetView<String> deletedMemberIds = Sets.difference(oldMemberIds, currentMemberIds);
        oldMemberIds.removeAll(deletedMemberIds);

        SetView<String> unchangedMemberIds = Sets.intersection(oldMemberIds, currentMemberIds);
        ImmutableSet.Builder<ServiceInstance> services = ImmutableSet.builder();
        for (String unchangedMemberId : unchangedMemberIds) {
          services.add(servicesByMemberId.get(unchangedMemberId));
        }

        // TODO(John Sirois): consider parallelizing fetches
        SetView<String> newMemberIds = Sets.difference(currentMemberIds, oldMemberIds);
        for (String newMemberId : newMemberIds) {
          // This get will trigger a fetch
          try {
            services.add(servicesByMemberId.get(newMemberId));
          } catch (ComputationException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof ServiceInstanceDeletedException)) {
              Throwables.propagateIfInstanceOf(cause, ServiceInstanceFetchException.class);
              throw new IllegalStateException(
                  "Unexpected error fetching member data for: " + newMemberId, e);
            }
          }
        }

        notifyServerSetChange(services.build());
      }
    }

    private void notifyServerSetChange(ImmutableSet<ServiceInstance> currentServerSet) {
      // ZK nodes may have changed if there was a session expiry for a server in the server set, but
      // if the server's status has not changed, we can skip any onChange updates.
      if (!currentServerSet.equals(serverSet)) {
        serverSet = currentServerSet;
        if (serverSet.isEmpty()) {
          LOG.warning("server set empty!");
        } else {
          LOG.info("server set change to:\n\t" + Joiner.on("\n\t").join(serverSet));
        }
        monitor.onChange(serverSet);
      }
    }
  }
}
