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

import com.google.common.collect.Sets;
import com.twitter.common.base.Closure;
import com.twitter.common.testing.EasyMockTest;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.net.loadbalancing.LoadBalancingStrategy.ConnectionResult;
import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Set;

import static org.easymock.EasyMock.capture;
import static org.hamcrest.CoreMatchers.anyOf;
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
  public void testHandlesEqualCount() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    backendOfferExpectation.offerBackends(BACKEND_1, BACKEND_2, BACKEND_3);
    connect(BACKEND_1, 5);
    connect(BACKEND_2, 5);
    connect(BACKEND_3, 5);

    assertThat(leastCon.nextBackend(), anyOf(is(BACKEND_1), is(BACKEND_2), is(BACKEND_3)));
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
  public void testUsesAllBackends() throws ResourceExhaustedException {
    BackendOfferExpectation backendOfferExpectation = new BackendOfferExpectation();
    control.replay();

    Set<String> allBackends = Sets.newHashSet(BACKEND_1, BACKEND_2, BACKEND_3);
    backendOfferExpectation.offerBackends(allBackends);

    Set<String> usedBackends = Sets.newHashSet();
    for (int i = 0; i < allBackends.size(); i++) {
      String backend = leastCon.nextBackend();
      usedBackends.add(backend);
      connect(backend, 1);
      disconnect(backend, 1);
    }

    assertThat(usedBackends, is(allBackends));
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
    for (int i = 0; i < count; i++) {
      leastCon.addConnectResult(backend, ConnectionResult.SUCCESS, 0L);
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

    void offerBackends(String ... backends) {
      offerBackends(Sets.newHashSet(backends));
    }

    void offerBackends(Set<String> backends) {
      leastCon.offerBackends(backends, onBackendsChosen);

      assertTrue(chosenBackends.hasCaptured());
      assertEquals(backends, Sets.newHashSet(chosenBackends.getValue()));
    }
  }
}
