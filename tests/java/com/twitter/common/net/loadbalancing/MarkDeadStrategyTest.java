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

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import com.google.common.base.Predicate;
import com.twitter.common.base.Closure;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.net.loadbalancing.LoadBalancingStrategy.ConnectionResult;
import com.twitter.common.net.loadbalancing.RequestTracker.RequestResult;
import com.twitter.common.util.BackoffDecider;
import com.twitter.common.util.Random;
import com.twitter.common.util.TruncatedBinaryBackoff;
import com.twitter.common.util.testing.FakeClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.easymock.EasyMock.expect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class MarkDeadStrategyTest extends EasyMockTest {

  private static final Amount<Long, Time> INITIAL_BACKOFF = Amount.of(1L, Time.SECONDS);
  private static final Amount<Long, Time> MAX_BACKOFF = Amount.of(10L, Time.SECONDS);

  private static final String BACKEND_1 = "backend1";
  private static final String BACKEND_2 = "backend2";

  private LoadBalancingStrategy<String> wrappedStrategy;
  private Closure<Collection<String>> onBackendsChosen;
  private Predicate<String> mockHostChecker;

  private LoadBalancingStrategy<String> markDead;
  private Random random;
  private FakeClock clock;

  @Before
  public void setUp() {
    wrappedStrategy = createMock(new Clazz<LoadBalancingStrategy<String>>() {});
    onBackendsChosen = createMock(new Clazz<Closure<Collection<String>>>() {});
    mockHostChecker = createMock(new Clazz<Predicate<String>>() {});

    random = createMock(Random.class);
    clock = new FakeClock();

    Function<String, BackoffDecider> backoffFactory =
        new Function<String, BackoffDecider>() {
          @Override public BackoffDecider apply(String s) {
            return BackoffDecider.builder(s)
                .withSeedSize(1)
                .withClock(clock)
                .withRandom(random)
                .withTolerateFailureRate(0.5)
                .withStrategy(new TruncatedBinaryBackoff(INITIAL_BACKOFF, MAX_BACKOFF))
                // This recovery type is suggested for load balancer strategies to prevent
                // connection pool churn that would occur from the random linear recovery type.
                .withRecoveryType(BackoffDecider.RecoveryType.FULL_CAPACITY)
                .withRequestWindow(MAX_BACKOFF)
                .build();
          }
        };

    markDead = new MarkDeadStrategy<String>(wrappedStrategy, backoffFactory, mockHostChecker);

  }

  @After
  public void verify() {
    control.verify();
  }

  @Test(expected = ResourceExhaustedException.class)
  public void testNoBackends() throws ResourceExhaustedException {
    expect(wrappedStrategy.nextBackend()).andThrow(new ResourceExhaustedException("No backends."));

    control.replay();

    markDead.nextBackend();
  }

  @Test
  public void testForwardsBasicCalls() throws ResourceExhaustedException {
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expect(wrappedStrategy.nextBackend()).andReturn(BACKEND_1);

    control.replay();

    markDead.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    assertThat(markDead.nextBackend(), is(BACKEND_1));
  }

  @Test
  public void testAllHealthy() {
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);
    expectRequest(BACKEND_1, RequestResult.SUCCESS, 10);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 10);
    expectRequest(BACKEND_2, RequestResult.SUCCESS, 10);

    control.replay();

    markDead.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    request(BACKEND_1, RequestResult.SUCCESS, connect(BACKEND_1, ConnectionResult.SUCCESS, 10));
    request(BACKEND_2, RequestResult.SUCCESS, connect(BACKEND_2, ConnectionResult.SUCCESS, 10));
  }

  @Test
  public void testOneFailingConnections() {
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 4);
    expectConnected(BACKEND_2, ConnectionResult.FAILED, 4);
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1), onBackendsChosen);

    control.replay();

    markDead.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    connect(BACKEND_1, ConnectionResult.SUCCESS, 10);
    connect(BACKEND_2, ConnectionResult.SUCCESS, 4);
    connect(BACKEND_2, ConnectionResult.FAILED, 10);
  }

  @Test
  public void testOneFailingRequests() {
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);
    expectRequest(BACKEND_1, RequestResult.SUCCESS, 10);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 10);
    expectRequest(BACKEND_2, RequestResult.SUCCESS, 10);
    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);
    expectRequest(BACKEND_1, RequestResult.FAILED, 30);
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_2), onBackendsChosen);

    control.replay();

    markDead.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    request(BACKEND_1, RequestResult.SUCCESS, connect(BACKEND_1, ConnectionResult.SUCCESS, 10));
    request(BACKEND_2, RequestResult.SUCCESS, connect(BACKEND_2, ConnectionResult.SUCCESS, 10));
    connect(BACKEND_1, ConnectionResult.SUCCESS, 10);
    request(BACKEND_1, RequestResult.FAILED, 50);
  }

  @Test
  public void testOneTimingOut() {
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);
    expectRequest(BACKEND_1, RequestResult.SUCCESS, 10);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 10);
    expectRequest(BACKEND_2, RequestResult.SUCCESS, 10);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 10);
    expectRequest(BACKEND_2, RequestResult.TIMEOUT, 30);
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1), onBackendsChosen);

    control.replay();

    markDead.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    request(BACKEND_1, RequestResult.SUCCESS, connect(BACKEND_1, ConnectionResult.SUCCESS, 10));
    request(BACKEND_2, RequestResult.SUCCESS, connect(BACKEND_2, ConnectionResult.SUCCESS, 10));
    connect(BACKEND_2, ConnectionResult.SUCCESS, 10);
    request(BACKEND_2, RequestResult.TIMEOUT, 50);
  }

  @Test
  public void testFailingRecovers() {
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 4);
    expectConnected(BACKEND_2, ConnectionResult.FAILED, 4);

    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1), onBackendsChosen);

    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 9);

    expect(mockHostChecker.apply(BACKEND_2)).andReturn(true);

    control.replay();

    markDead.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    connect(BACKEND_1, ConnectionResult.SUCCESS, 10);
    connect(BACKEND_2, ConnectionResult.SUCCESS, 4);
    connect(BACKEND_2, ConnectionResult.FAILED, 5);

    connect(BACKEND_1, ConnectionResult.SUCCESS, 5);
    clock.advance(INITIAL_BACKOFF);  // Wait for backoff period to expire.
    clock.waitFor(1);
    clock.advance(INITIAL_BACKOFF);  // Wait for recovery period to expire.
    connect(BACKEND_1, ConnectionResult.SUCCESS, 5);
    connect(BACKEND_2, ConnectionResult.SUCCESS, 9);
  }

  @Test
  public void testFailingServerWithLiveHostChecker() {
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 4);
    expectConnected(BACKEND_2, ConnectionResult.FAILED, 4);

    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1), onBackendsChosen);

    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);

    expect(mockHostChecker.apply(BACKEND_2)).andReturn(false);

    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);

    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 5);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 10);

    expect(mockHostChecker.apply(BACKEND_2)).andReturn(true);

    control.replay();

    markDead.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);

    connect(BACKEND_1, ConnectionResult.SUCCESS, 10);
    connect(BACKEND_2, ConnectionResult.SUCCESS, 4);
    connect(BACKEND_2, ConnectionResult.FAILED, 5);

    connect(BACKEND_1, ConnectionResult.SUCCESS, 5);
    clock.advance(INITIAL_BACKOFF);  // Wait for backoff period to expire.
    clock.waitFor(1);
    clock.advance(INITIAL_BACKOFF);  // Wait for recovery period to expire.
    connect(BACKEND_1, ConnectionResult.SUCCESS, 5);
    clock.advance(INITIAL_BACKOFF);  // Wait for backoff period to expire.
    clock.waitFor(1);
    clock.advance(INITIAL_BACKOFF);  // Wait for recovery period to expire.
    connect(BACKEND_1, ConnectionResult.SUCCESS, 5);
    connect(BACKEND_2, ConnectionResult.SUCCESS, 10);
  }

  @Test
  public void testAllDead() {
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 10);
    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 10);
    expectConnected(BACKEND_1, ConnectionResult.FAILED, 10);
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_2, ConnectionResult.FAILED, 10);
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    expectConnected(BACKEND_2, ConnectionResult.FAILED, 5);

    control.replay();

    markDead.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    connect(BACKEND_1, ConnectionResult.SUCCESS, 10);
    connect(BACKEND_2, ConnectionResult.SUCCESS, 10);
    connect(BACKEND_1, ConnectionResult.FAILED, 15);
    connect(BACKEND_2, ConnectionResult.FAILED, 15);
  }

  @Test
  public void testRecoversFromForcedLiveMode() {
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);

    expectConnected(BACKEND_1, ConnectionResult.SUCCESS, 5);
    expectConnected(BACKEND_1, ConnectionResult.FAILED, 5);  // Backend 1 starts backing off.
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_2), onBackendsChosen);

    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 5);
    expectConnected(BACKEND_2, ConnectionResult.FAILED, 5);  // Backend 2 starts backing off.
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);

    expectConnected(BACKEND_2, ConnectionResult.SUCCESS, 5);
    expectConnected(BACKEND_2, ConnectionResult.FAILED, 5);  // Backend 2 starts backing off.
    wrappedStrategy.offerBackends(Sets.newHashSet(BACKEND_1), onBackendsChosen);

    control.replay();

    markDead.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2), onBackendsChosen);

    connect(BACKEND_1, ConnectionResult.SUCCESS, 5);
    connect(BACKEND_1, ConnectionResult.FAILED, 6);  // BACKEND_1 gets marked as dead.

    connect(BACKEND_2, ConnectionResult.SUCCESS, 5);
    connect(BACKEND_2, ConnectionResult.FAILED, 6);  // All now marked dead, forced into live mode.

    clock.advance(INITIAL_BACKOFF);  // Wait for backoff period to expire.
    clock.waitFor(1);
    connect(BACKEND_2, ConnectionResult.SUCCESS, 5);
    connect(BACKEND_2, ConnectionResult.FAILED, 5);  // BACKEND_2 marked as dead.
  }

  private int connect(String backend, ConnectionResult result, int count) {
    for (int i = 0; i < count; i++) {
      markDead.addConnectResult(backend, result, 0L);
    }
    return count;
  }

  private void request(String backend, RequestResult result, int count) {
    for (int i = 0; i < count; i++) {
      markDead.addRequestResult(backend, result, 0L);
    }
  }

  private void expectConnected(String backend, ConnectionResult result, int count) {
    for (int i = 0; i < count; i++) {
      wrappedStrategy.addConnectResult(backend, result, 0L);
    }
  }

  private void expectRequest(String backend, RequestResult result, int count) {
    for (int i = 0; i < count; i++) {
      wrappedStrategy.addRequestResult(backend, result, 0L);
    }
  }
}
