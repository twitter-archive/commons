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

package com.twitter.common.net.monitoring;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.net.loadbalancing.RequestTracker;
import com.twitter.common.util.Clock;

import javax.annotation.concurrent.GuardedBy;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors activity on established connections between two hosts.  This can be used for a server
 * to track inbound clients, or for a client to track requests sent to different servers.
 *
 * The monitor will retain information for hosts that may no longer be active, but will expunge
 * information for hosts that have been idle for more than five minutes.
 *
 * @author William Farner
 */
public class TrafficMonitor<K> implements ConnectionMonitor<K>, RequestTracker<K> {

  @VisibleForTesting
  static final Amount<Long, Time> DEFAULT_GC_INTERVAL = Amount.of(5L, Time.MINUTES);

  @GuardedBy("this")
  private final Map<K, TrafficInfo> trafficInfos;

  private final String serviceName;
  private final Amount<Long, Time> gcInterval;

  private AtomicLong lifetimeRequests = new AtomicLong();
  private final Clock clock;

  /**
   * Creates a new traffic monitor using the default cleanup interval.
   *
   * @param serviceName Name of the service to monitor, used for creating variable names.
   */
  public TrafficMonitor(final String serviceName) {
    this(serviceName, DEFAULT_GC_INTERVAL);
  }

  /**
   * Creates a new traffic monitor with a custom cleanup interval.
   *
   * @param serviceName Service name for the monitor.
   * @param gcInterval Interval on which the remote host garbage collector should run.
   */
  public TrafficMonitor(final String serviceName, Amount<Long, Time> gcInterval) {
    this(serviceName, gcInterval, Clock.SYSTEM_CLOCK);
  }

  /**
   * Convenience method to create a typed traffic monitor.
   *
   * @param serviceName Service name for the monitor.
   * @param <T> Monitor type.
   * @return A new traffic monitor.
   */
  public static <T> TrafficMonitor<T> create(String serviceName) {
    return new TrafficMonitor<T>(serviceName);
  }

  @VisibleForTesting
  TrafficMonitor(final String serviceName, Clock clock) {
    this(serviceName, DEFAULT_GC_INTERVAL, clock);
  }

  private TrafficMonitor(final String serviceName, Amount<Long, Time> gcInterval, Clock clock) {
    this.serviceName = MorePreconditions.checkNotBlank(serviceName);
    this.clock = Preconditions.checkNotNull(clock);
    Preconditions.checkNotNull(gcInterval);
    Preconditions.checkArgument(gcInterval.getValue() > 0, "GC interval must be > zero.");
    this.gcInterval = gcInterval;

    trafficInfos = new MapMaker().makeComputingMap(new Function<K, TrafficInfo>() {
      @Override public TrafficInfo apply(K key) { return new TrafficInfo(key); }
    });

    Runnable gc = new Runnable() {
        @Override public void run() { gc(); }
    };

    ScheduledExecutorService gcExecutor =
        new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setDaemon(true)
            .setNameFormat("TrafficMonitor-gc-%d").build());
    gcExecutor.scheduleAtFixedRate(gc, gcInterval.as(Time.SECONDS), gcInterval.as(Time.SECONDS),
        TimeUnit.SECONDS);
  }

  /**
   * Gets the name of the service that this monitor is monitoring.
   *
   * @return Monitor's service name.
   */
  public String getServiceName() {
    return serviceName;
  }

  /**
   * Gets the total number of requests that this monitor has observed, for all remote hosts.
   *
   * @return Total number of requests observed.
   */
  public long getLifetimeRequestCount() {
    return lifetimeRequests.get();
  }

  /**
   * Fetches all current traffic information.
   *
   * @return A map from the host key type to information about that host.
   */
  public synchronized Map<K, TrafficInfo> getTrafficInfo() {
    return ImmutableMap.copyOf(trafficInfos);
  }

  @Override
  public synchronized void connected(K key) {
    Preconditions.checkNotNull(key);

    trafficInfos.get(key).incConnections();
  }

  @Override
  public synchronized void released(K key) {
    Preconditions.checkNotNull(key);

    TrafficInfo info = trafficInfos.get(key);

    Preconditions.checkState(info.getConnectionCount() > 0, "Double release detected!");
    info.decConnections();
  }

  @Override
  public void requestResult(K key, RequestResult result, long requestTimeNanos) {
    Preconditions.checkNotNull(key);

    lifetimeRequests.incrementAndGet();
    trafficInfos.get(key).addResult(result);
  }

  @VisibleForTesting
  synchronized void gc() {
    Iterables.removeIf(trafficInfos.entrySet(),
        new Predicate<Map.Entry<K, TrafficInfo>>() {
          @Override public boolean apply(Map.Entry<K, TrafficInfo> clientInfo) {
            if (clientInfo.getValue().connections.get() > 0) return false;

            long idlePeriod = clock.nowNanos() - clientInfo.getValue().getLastActiveTimestamp();

            return idlePeriod > gcInterval.as(Time.NANOSECONDS);
          }
        });
  }

  /**
   * Information about traffic obsserved to/from a specific host.
   */
  public class TrafficInfo {
    private final K key;
    private AtomicInteger requestSuccesses = new AtomicInteger();
    private AtomicInteger requestFailures = new AtomicInteger();
    private AtomicInteger connections = new AtomicInteger();
    private AtomicLong lastActive = new AtomicLong();

    TrafficInfo(K key) {
      this.key = key;
      pulse();
    }

    void pulse() {
      lastActive.set(clock.nowNanos());
    }

    public K getKey() {
      return key;
    }

    void addResult(RequestResult result) {
      pulse();
      switch (result) {
        case SUCCESS:
          requestSuccesses.incrementAndGet();
          break;
        case FAILED:
        case TIMEOUT:
          requestFailures.incrementAndGet();
          break;
      }
    }

    public int getRequestSuccessCount() {
      return requestSuccesses.get();
    }

    public int getRequestFailureCount() {
      return requestFailures.get();
    }

    int incConnections() {
      pulse();
      return connections.incrementAndGet();
    }

    int decConnections() {
      pulse();
      return connections.decrementAndGet();
    }

    public int getConnectionCount() {
      return connections.get();
    }

    public long getLastActiveTimestamp() {
      return lastActive.get();
    }
  }
}
