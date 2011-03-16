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

package com.twitter.common.net.loadbalancing;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.twitter.common.net.pool.ResourceExhaustedException;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

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
   * Wraps an int, so that we can be comparable but will hash only by reference.
   *
   * Yes, i know this is a horrible bastardization of the equals() and compareTo() contract. -Bill
   */
  private class ConnectionStats implements Comparable<ConnectionStats> {
    final S connectionKey;
    int value = 0;      // Stores the total number of active connections.
    long useCount = 0;  // Stores the total number times a connection has been used.

    ConnectionStats(S connectionKey) {
      this.connectionKey = connectionKey;
    }

    @Override public int compareTo(ConnectionStats other) {
      // Sort by number of active connections first.
      int difference = value - other.value;
      if (difference != 0) return difference;

      // Sub-sort by total number of times a connection has been used (this will ensure that
      // all backends are exercised).
      long useDifference = useCount - other.useCount;
      if (useDifference != 0) return (int) useDifference;

      // If the above two are equal, sort by object reference.
      return hashCode() - other.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      @SuppressWarnings("unchecked")
      ConnectionStats other = (ConnectionStats) o;
      return compareTo(other) == 0 && connectionKey.equals(other.connectionKey);
    }

    @Override public String toString() {
      return String.format("%d-%d", value, useCount);
    }
  }

  @Override
  protected Collection<S> onBackendsOffered(Set<S> backends) {
    Map<S, ConnectionStats> newConnections = Maps.newHashMapWithExpectedSize(backends.size());
    Collection<ConnectionStats> newConnectionStats = Lists.newArrayListWithCapacity(backends.size());

    for (S backend : backends) {
      ConnectionStats stats = connections.get(backend);
      if (stats == null) {
        stats = new ConnectionStats(backend);
      }

      // Reset use counts on all backends to prevent dogpiling on new servers.
      stats.useCount = 0;
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

    if (connectionStats.isEmpty()) throw new ResourceExhaustedException("No backends.");

    return connectionStats.first().connectionKey;
  }

  @Override
  public void addConnectResult(S backendKey, ConnectionResult result, long connectTimeNanos) {
    Preconditions.checkNotNull(backendKey);
    Preconditions.checkState(connections.size() == connectionStats.size());
    Preconditions.checkNotNull(result);

    if (result == ConnectionResult.SUCCESS) {
      ConnectionStats stats = connections.get(backendKey);
      Preconditions.checkNotNull(stats);

      Preconditions.checkState(connectionStats.remove(stats));
      stats.value++;
      stats.useCount++;
      Preconditions.checkState(connectionStats.add(stats));
    }
  }

  @Override
  public void connectionReturned(S backendKey) {
    Preconditions.checkNotNull(backendKey);
    Preconditions.checkState(connections.size() == connectionStats.size());

    ConnectionStats stats = connections.get(backendKey);
    Preconditions.checkNotNull(stats);

    if (stats.value > 0) {
      Preconditions.checkState(connectionStats.remove(stats));
      stats.value--;
      Preconditions.checkState(connectionStats.add(stats));
    } else {
      LOG.warning("connection stats dropped below zero, ignoring");
    }
  }
}
