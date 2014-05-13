package com.twitter.common.zookeeper;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import com.twitter.common.base.Command;
import com.twitter.common.base.Commands;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.thrift.Endpoint;
import com.twitter.thrift.ServiceInstance;
import com.twitter.thrift.Status;

/**
 * A server set that represents a fixed set of hosts.
 * This may be composed under {@link CompoundServerSet} to ensure a minimum set of hosts is
 * present.
 * A static server set does not support joining, but will allow normal join calls and status update
 * calls to be made.
 */
public class StaticServerSet implements ServerSet {

  private static final Logger LOG = Logger.getLogger(StaticServerSet.class.getName());

  private static final Function<Endpoint, ServiceInstance> ENDPOINT_TO_INSTANCE =
      new Function<Endpoint, ServiceInstance>() {
        @Override public ServiceInstance apply(Endpoint endpoint) {
          return new ServiceInstance(endpoint, ImmutableMap.<String, Endpoint>of(), Status.ALIVE);
        }
      };

  private final ImmutableSet<ServiceInstance> hosts;

  /**
   * Creates a static server set that will reply to monitor calls immediately and exactly once with
   * the provided service instances.
   *
   * @param hosts Hosts in the static set.
   */
  public StaticServerSet(Set<ServiceInstance> hosts) {
    this.hosts = ImmutableSet.copyOf(hosts);
  }

  /**
   * Creates a static server set containing the provided endpoints (and no auxiliary ports) which
   * will all be in the {@link Status#ALIVE} state.
   *
   * @param endpoints Endpoints in the static set.
   * @return A static server set that will advertise the provided endpoints.
   */
  public static StaticServerSet fromEndpoints(Set<Endpoint> endpoints) {
    return new StaticServerSet(
        ImmutableSet.copyOf(Iterables.transform(endpoints, ENDPOINT_TO_INSTANCE)));
  }

  private EndpointStatus join(
      InetSocketAddress endpoint,
      Map<String, InetSocketAddress> auxEndpoints,
      Optional<Integer> shardId) {

    LOG.warning("Attempt to join fixed server set ignored.");
    ServiceInstance joining = new ServiceInstance(
        ServerSets.toEndpoint(endpoint),
        Maps.transformValues(auxEndpoints, ServerSets.TO_ENDPOINT),
        Status.ALIVE);
    if (shardId.isPresent()) {
      joining.setShard(shardId.get());
    }
    if (!hosts.contains(joining)) {
      LOG.log(Level.SEVERE,
          "Joining instance " + joining + " does not match any member of the static set.");
    }

    return new EndpointStatus() {
      @Override public void leave() throws UpdateException {
        LOG.warning("Attempt to adjust state of fixed server set ignored.");
      }

      @Override public void update(Status status) throws UpdateException {
        LOG.warning("Attempt to adjust state of fixed server set ignored.");
      }
    };
  }

  @Override
  public EndpointStatus join(
      InetSocketAddress endpoint,
      Map<String, InetSocketAddress> auxEndpoints,
      Status status) {

    LOG.warning("This method is deprecated. Please do not specify a status field.");
    return join(endpoint, auxEndpoints, Optional.<Integer>absent());
  }

  @Override
  public EndpointStatus join(
      InetSocketAddress endpoint,
      Map<String, InetSocketAddress> auxEndpoints) {

    LOG.warning("Joining a ServerSet without a shard ID is deprecated and will soon break.");
    return join(endpoint, auxEndpoints, Optional.<Integer>absent());
  }

  @Override
  public EndpointStatus join(
      InetSocketAddress endpoint,
      Map<String, InetSocketAddress> auxEndpoints,
      int shardId) throws JoinException, InterruptedException {

    return join(endpoint, auxEndpoints, Optional.of(shardId));
  }

  @Override
  public Command watch(HostChangeMonitor<ServiceInstance> monitor) {
    monitor.onChange(hosts);
    return Commands.NOOP;
  }

  @Override
  public void monitor(HostChangeMonitor<ServiceInstance> monitor) throws MonitorException {
    watch(monitor);
  }
}
