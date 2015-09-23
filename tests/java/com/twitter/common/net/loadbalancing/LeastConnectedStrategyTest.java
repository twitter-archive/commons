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

import com.google.common.collect.ImmutableSet;

import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.Closure;
import com.twitter.common.net.loadbalancing.LoadBalancingStrategy.ConnectionResult;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.capture;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author William Farner
 */
public class LeastConnectedStrategyTest extends EasyMockTest {

  private static final String BACKEND_1 = "backend1";
  private static final String BACKEND_2 = "backend2";
  private static final String BACKEND_3 = "backend3";
  private static final String BACKEND_4 = "backend4";

  private Closure<Collection<String>> onBackendsChosen;

  private LoadBalancingStrategy<String> leastCon;

  @Before
  public void setUp() {
    onBackendsChosen = createMock(new Clazz<Closure<Collection<String>>>() {});

    leastCon = new LeastConnectedStrategy<String>();
  }

  @Test(expected = ResourceExhaustedException.class)
  public void testNoBackends() throws ResourceExhaustedException {
    control.replay();

    leastCon.nextBackend();
  }

  @Test(expected = ResourceExhaustedException.class)
  public void testEmptyBackends() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    backendOfferExpectation.offerBackends();

    leastCon.nextBackend();
  }

  @Test
  public void testPicksLeastConnected() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    backendOfferExpectation.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3);

    connect(BACKEND_1, 1);
    connect(BACKEND_2, 2);
    connect(BACKEND_3, 3);
    assertThat(leastCon.nextBackend(), is(BACKEND_1));

    connect(BACKEND_1, 2);
    assertThat(leastCon.nextBackend(), is(BACKEND_2));
  }

  @Test
  public void testPicksUnconnected() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    backendOfferExpectation.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3);
    connect(BACKEND_1, 1);
    connect(BACKEND_2, 2);

    assertThat(leastCon.nextBackend(), is(BACKEND_3));
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in varargs.
  public void testHandlesEqualCount() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    backendOfferExpectation.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3);
    connect(BACKEND_1, 5);
    connect(BACKEND_2, 5);
    connect(BACKEND_3, 5);

    assertTrue(ImmutableSet.of(BACKEND_1, BACKEND_2, BACKEND_3).contains(leastCon.nextBackend()));
  }

  @Test
  public void testReranks() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    backendOfferExpectation.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3);
    connect(BACKEND_1, 10);
    connect(BACKEND_2, 5);
    connect(BACKEND_3, 5);

    disconnect(BACKEND_1, 6);

    assertThat(leastCon.nextBackend(), is(BACKEND_1));
  }

  @Test
  public void testUsesAllBackends_success() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    ImmutableSet<String> allBackends = ImmutableSet.of(BACKEND_1, BACKEND_2, BACKEND_3);
    backendOfferExpectation.offerBackends(allBackends);

    ImmutableSet.Builder<String> usedBackends = ImmutableSet.builder();
    for (int i = 0; i < allBackends.size(); i++) {
      String backend = leastCon.nextBackend();
      usedBackends.add(backend);
      connect(backend, 1);
      disconnect(backend, 1);
    }

    assertThat(usedBackends.build(), is(allBackends));
  }

  @Test
  public void UsesAllBackends_mixed() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    backendOfferExpectation.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3, BACKEND_4);

    connect(BACKEND_1, ConnectionResult.FAILED, 1);
    assertThat(leastCon.nextBackend(), is(BACKEND_2));

    connect(BACKEND_2, ConnectionResult.FAILED, 1);
    assertThat(leastCon.nextBackend(), is(BACKEND_3));

    connect(BACKEND_3, 1);
    assertThat(leastCon.nextBackend(), is(BACKEND_4));

    connect(BACKEND_4, 1);

    // Now we should rotate around to the front and give the connection failure another try.
    assertThat(leastCon.nextBackend(), is(BACKEND_1));
  }

  @Test
  public void testUsesAllBackends_failure() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    ImmutableSet<String> allBackends = ImmutableSet.of(BACKEND_1, BACKEND_2, BACKEND_3);
    backendOfferExpectation.offerBackends(allBackends);

    ImmutableSet.Builder<String> usedBackends = ImmutableSet.builder();
    for (int i = 0; i < allBackends.size(); i++) {
      String backend = leastCon.nextBackend();
      usedBackends.add(backend);
      connect(backend, ConnectionResult.FAILED, 1);
    }

    assertThat(usedBackends.build(), is(allBackends));
  }

  @Test
  public void testUsedLeastExhausted() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    backendOfferExpectation.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3);
    connect(BACKEND_1, 10);
    disconnect(BACKEND_1, 10);
    connect(BACKEND_3, 5);
    disconnect(BACKEND_3, 5);

    assertThat(leastCon.nextBackend(), is(BACKEND_2));
  }

  @Test
  public void testNoNegativeCounts() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    backendOfferExpectation.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3);
    connect(BACKEND_1, 1);
    connect(BACKEND_3, 1);

    // If there was a bug allowing connection count to go negative, BACKEND_1 would be chosen,
    // but if it floors at zero, BACKEND_2 will be the lowest.
    disconnect(BACKEND_1, 5);
  }

  @Test
  public void testForgetsOldBackends() throws ResourceExhaustedException {
    BackendOfferExpectation offer1 = new BackendOfferExpectation();
    BackendOfferExpectation offer2 = new BackendOfferExpectation();
    BackendOfferExpectation offer3 = new BackendOfferExpectation();
    control.replay();

    offer1.offerBackends(BACKEND_1, BACKEND_2);
    connect(BACKEND_2, 10);

    offer2.offerBackends(BACKEND_2, BACKEND_3);
    connect(BACKEND_3, 1);
    assertThat(leastCon.nextBackend(), is(BACKEND_3));

    offer3.offerBackends(BACKEND_2);
    assertThat(leastCon.nextBackend(), is(BACKEND_2));
  }

  @Test
  public void testAccountingSurvivesBackendChange() throws ResourceExhaustedException {
    BackendOfferExpectation offer1 = new BackendOfferExpectation();
    BackendOfferExpectation offer2 = new BackendOfferExpectation();
    control.replay();

    offer1.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3, BACKEND_4);
    connect(BACKEND_1, 10);
    connect(BACKEND_2, 8);
    connect(BACKEND_3, 9);
    assertThat(leastCon.nextBackend(), is(BACKEND_4));

    offer2.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3);
    assertThat(leastCon.nextBackend(), is(BACKEND_2));
  }

  private void connect(String backend, int count) {
    connect(backend, ConnectionResult.SUCCESS, count);
  }

  private void connect(String backend, ConnectionResult result, int count) {
    for (int i = 0; i < count; i++) {
      leastCon.addConnectResult(backend, result, 0L);
    }
  }

  private void disconnect(String backend, int count) {
    for (int i = 0; i < count; i++) {
      leastCon.connectionReturned(backend);
    }
  }

  private class BackendOfferExpectation {
    private final Capture<Collection<String>> chosenBackends;

    private BackendOfferExpectation() {
      chosenBackends = createCapture();
      onBackendsChosen.execute(capture(chosenBackends));
    }

    void offerBackends(String... backends) {
      offerBackends(ImmutableSet.copyOf(backends));
    }

    void offerBackends(ImmutableSet<String> backends) {
      leastCon.offerBackends(backends, onBackendsChosen);

      assertTrue(chosenBackends.hasCaptured());
      assertEquals(backends, ImmutableSet.copyOf(chosenBackends.getValue()));
    }
  }
}
