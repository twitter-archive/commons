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

import com.google.common.collect.Sets;
import com.twitter.common.base.Closure;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.net.loadbalancing.LoadBalancingStrategy.ConnectionResult;
import com.twitter.common.net.loadbalancing.RequestTracker.RequestResult;
import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Set;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author William Farner
 */
public class LoadBalancerImplTest extends EasyMockTest {

  private static final String BACKEND_1 = "backend1";
  private static final String BACKEND_2 = "backend2";

  private LoadBalancingStrategy<String> strategy;
  private Closure<Collection<String>> onBackendsChosen;

  private LoadBalancer<String> loadBalancer;

  @Before
  public void setUp() {
    strategy = createMock(new Clazz<LoadBalancingStrategy<String>>() {});
    onBackendsChosen = createMock(new Clazz<Closure<Collection<String>>>() {});

    loadBalancer = LoadBalancerImpl.create(this.strategy);
  }

  @Test
  public void testForwardsBasicCalls() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation =
        new BackendOfferExpectation(BACKEND_1, BACKEND_2);
    expect(strategy.nextBackend()).andReturn(BACKEND_1);
    strategy.addConnectResult(BACKEND_1, ConnectionResult.SUCCESS, 0L);
    strategy.connectionReturned(BACKEND_1);
    strategy.addConnectResult(BACKEND_1, ConnectionResult.TIMEOUT, 0L);

    control.replay();

    backendOfferExpectation.simulateBackendsChosen();

    assertThat(loadBalancer.nextBackend(), is(BACKEND_1));
    loadBalancer.connected(BACKEND_1, 0L);
    loadBalancer.released(BACKEND_1);
    loadBalancer.connectFailed(BACKEND_1, ConnectionResult.TIMEOUT);
  }

  @Test
  public void testHandlesUnknownBackend() {
    BackendOfferExpectation first = new BackendOfferExpectation(BACKEND_1, BACKEND_2);
    BackendOfferExpectation second = new BackendOfferExpectation(BACKEND_1);

    strategy.addConnectResult(BACKEND_1, ConnectionResult.SUCCESS, 0L);
    strategy.connectionReturned(BACKEND_1);

    BackendOfferExpectation third = new BackendOfferExpectation(BACKEND_1, BACKEND_2);

    strategy.addConnectResult(BACKEND_1, ConnectionResult.SUCCESS, 0L);
    strategy.addConnectResult(BACKEND_2, ConnectionResult.SUCCESS, 0L);

    BackendOfferExpectation fourth = new BackendOfferExpectation(BACKEND_1);

    strategy.addRequestResult(BACKEND_1, RequestResult.SUCCESS, 0L);
    strategy.connectionReturned(BACKEND_1);

    control.replay();

    first.simulateBackendsChosen();
    second.simulateBackendsChosen();

    loadBalancer.connected(BACKEND_1, 0L);
    loadBalancer.released(BACKEND_1);

    // Release an unrecognized connection, should not propagate to strategy.
    loadBalancer.released("foo");

    // Requests related to BACKEND_2 are not forwarded.
    loadBalancer.connected(BACKEND_2, 0L);
    loadBalancer.connectFailed(BACKEND_2, ConnectionResult.FAILED);
    loadBalancer.requestResult(BACKEND_2, RequestResult.SUCCESS, 0L);
    loadBalancer.released(BACKEND_2);

    third.simulateBackendsChosen();
    loadBalancer.connected(BACKEND_1, 0L);
    loadBalancer.connected(BACKEND_2, 0L);
    fourth.simulateBackendsChosen();
    loadBalancer.requestResult(BACKEND_1, RequestResult.SUCCESS, 0L);
    loadBalancer.requestResult(BACKEND_2, RequestResult.SUCCESS, 0L);
    loadBalancer.released(BACKEND_1);
    loadBalancer.released(BACKEND_2);
  }

  private class BackendOfferExpectation {
    private final Set<String> backends;
    private final Capture<Closure<Collection<String>>> onBackendsChosenCapture;

    private BackendOfferExpectation(String ... backends) {
      this.backends = Sets.newHashSet(backends);
      onBackendsChosenCapture = createCapture();

      strategy.offerBackends(eq(this.backends), capture(onBackendsChosenCapture));
      onBackendsChosen.execute(this.backends);
    }

    void simulateBackendsChosen() {
      loadBalancer.offerBackends(backends, onBackendsChosen);
      assertTrue(onBackendsChosenCapture.hasCaptured());

      // Simulate the strategy notifying LoadBalancer's callback of a backend choice
      onBackendsChosenCapture.getValue().execute(backends);
    }
  }
}
