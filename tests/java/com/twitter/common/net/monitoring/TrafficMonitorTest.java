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

import com.twitter.common.net.loadbalancing.RequestTracker;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.testing.FakeClock;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class TrafficMonitorTest {

  private final String HOST_A = "hostA";
  private final String HOST_B = "hostB";

  private FakeClock clock;
  private TrafficMonitor<String> monitor;

  @Before
  public void setUp() {
    clock = new FakeClock();
    monitor = new TrafficMonitor<String>("test service", clock);
  }

  @Test
  public void testBasicFlow() {
    monitor.connected(HOST_A);
    addSuccess(HOST_A);

    verifyConnections(HOST_A, 1);
    verifyRequests(HOST_A, 1);

    monitor.released(HOST_A);

    verifyConnections(HOST_A, 0);
    verifyRequests(HOST_A, 1);
    verifyLifetimeRequests(1);
  }

  @Test
  public void testOutOfOrder() {
    addSuccess(HOST_A);
    monitor.connected(HOST_A);

    verifyConnections(HOST_A, 1);
    verifyRequests(HOST_A, 1);
    verifyLifetimeRequests(1);
  }

  @Test
  public void testEntriesExpire() {
    monitor.connected(HOST_A);
    addSuccess(HOST_A);
    monitor.released(HOST_A);

    verifyConnections(HOST_A, 0);
    verifyRequests(HOST_A, 1);

    monitor.connected(HOST_B);
    addSuccess(HOST_B);

    verifyConnections(HOST_B, 1);
    verifyRequests(HOST_B, 1);

    // Fake
    clock.advance(Amount.of(TrafficMonitor.DEFAULT_GC_INTERVAL.as(Time.SECONDS) + 1, Time.SECONDS));
    monitor.gc();

    verifyConnections(HOST_A, 0);
    verifyRequests(HOST_A, 0);
    verifyConnections(HOST_B, 1);
    verifyRequests(HOST_B, 1);
    verifyLifetimeRequests(2);
  }

  private void addSuccess(String host) {
    monitor.requestResult(host, RequestTracker.RequestResult.SUCCESS, 0L);
  }

  private void verifyConnections(String host, int count) {
    TrafficMonitor<String>.TrafficInfo info = monitor.getTrafficInfo().get(host);

    if (count > 0) assertNotNull(info);

    if (info != null) {
      assertThat(monitor.getTrafficInfo().get(host).getConnectionCount(), is(count));
    }
  }

  private void verifyRequests(String host, int count) {
    TrafficMonitor<String>.TrafficInfo info = monitor.getTrafficInfo().get(host);

    if (count > 0) assertNotNull(info);

    if (info != null) {
      assertThat(monitor.getTrafficInfo().get(host).getRequestSuccessCount(), is(count));
    }
  }

  private void verifyLifetimeRequests(long count) {
    assertThat(monitor.getLifetimeRequestCount(), is(count));
  }
}
