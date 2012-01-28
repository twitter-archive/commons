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

package com.twitter.common.net.loadbalancing;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.twitter.common.net.pool.ResourceExhaustedException;

/**
 * A load balancer that attempts to direct load towards a backend that has the fewest leased
 * connections.
 *
 * @author William Farner
 */
public class LeastConnectedStrategy<S> extends StaticLoadBalancingStrategy<S> {
  private static final Logger LOG = Logger.getLogger(LeastConnectedStrategy.class.getName());

  // Maps from backends to the number of connections made to them.
  private final Map<S, ConnectionStats> connections = Maps.newHashMap();

  // Manages sorting of connection counts, with a reference back to the backend.
  private final SortedSet<ConnectionStats> connectionStats = Sets.newTreeSet();

  /**
   * Encapsulates a set of connection stats that allow connections to be sorted as per the least
   * connected strategy.
   */
  private class ConnectionStats implements Comparable<ConnectionStats> {
    final S connectionKey;
    final int connectionId;
    int activeCount = 0; // Stores the total number of active connections.
    long useCount = 0;  // Stores the total number times a connection has been used.

    ConnectionStats(S connectionKey, int connectionId) {
      this.connectionKey = connectionKey;
      this.connectionId = connectionId;
    }

    @Override
    public int compareTo(ConnectionStats other) {
      // Sort by number of active connections first.
      int difference = activeCount - other.activeCount;
      if (difference != 0) {
        return difference;
      }

      // Sub-sort by total number of times a connection has been used (this will ensure that
      // all backends are exercised).
      long useDifference = useCount - other.useCount;
      if (useDifference != 0) {
        return Long.signum(useDifference);
      }

      // If the above two are equal, break the tie using the connection id.
      return connectionId - other.connectionId;
    }

    @Override
    public boolean equals(Object o) {
      // We use ConnectionStats in a sorted container and so we need to have an equals
      // implementation consistent with compareTo, ie:
      // (x.compareTo(y) == 0) == x.equals(y)
      // We accomplish this directly.

      @SuppressWarnings("unchecked")
      ConnectionStats other = (ConnectionStats) o;
      return compareTo(other) == 0;
    }

    @Override
    public String toString() {
      return String.format("%d-%d", activeCount, useCount);
    }
  }

  @Override
  protected Collection<S> onBackendsOffered(Set<S> backends) {
    Map<S, ConnectionStats> newConnections = Maps.newHashMapWithExpectedSize(backends.size());
    Collection<ConnectionStats> newConnectionStats =
        Lists.newArrayListWithCapacity(backends.size());

    // Recreate all connection stats since their ordering may have changed and this is used for
    // comparison tie breaks.
    int backendId = 0;
    for (S backend : backends) {
      ConnectionStats stats = new ConnectionStats(backend, backendId++);

      // Retain the activeCount for existing backends to prevent dogpiling existing active servers
      ConnectionStats existing = connections.get(backend);
      if (existing != null) {
        stats.activeCount = existing.activeCount;
      }

      newConnections.put(backend, stats);
      newConnectionStats.add(stats);
    }

    connections.clear();
    connections.putAll(newConnections);
    connectionStats.clear();
    connectionStats.addAll(newConnectionStats);

    return connections.keySet();
  }

  @Override
  public S nextBackend() throws ResourceExhaustedException {
    Preconditions.checkState(connections.size() == connectionStats.size());

    if (connectionStats.isEmpty()) {
      throw new ResourceExhaustedException("No backends.");
    }

    return connectionStats.first().connectionKey;
  }

  @Override
  public void addConnectResult(S backendKey, ConnectionResult result, long connectTimeNanos) {
    Preconditions.checkNotNull(backendKey);
    Preconditions.checkState(connections.size() == connectionStats.size());
    Preconditions.checkNotNull(result);

    ConnectionStats stats = connections.get(backendKey);
    Preconditions.checkNotNull(stats);

    Preconditions.checkState(connectionStats.remove(stats));
    if (result == ConnectionResult.SUCCESS) {
      stats.activeCount++;
    }
    stats.useCount++;
    Preconditions.checkState(connectionStats.add(stats));
  }

  @Override
  public void connectionReturned(S backendKey) {
    Preconditions.checkNotNull(backendKey);
    Preconditions.checkState(connections.size() == connectionStats.size());

    ConnectionStats stats = connections.get(backendKey);
    Preconditions.checkNotNull(stats);

    if (stats.activeCount > 0) {
      Preconditions.checkState(connectionStats.remove(stats));
      stats.activeCount--;
      Preconditions.checkState(connectionStats.add(stats));
    } else {
      LOG.warning("connection stats dropped below zero, ignoring");
    }
  }
}
