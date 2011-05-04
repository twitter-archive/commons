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

package com.twitter.common.util;

import com.google.common.collect.Sets;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.testing.FakeClock;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class BackoffDeciderTest {
  private static final String NAME = "test_decider";

  private IMocksControl control;

  private FakeClock clock;
  private Random random;

  @Before
  public void setUp() {
    control = createControl();
    random = control.createMock(Random.class);

    clock = new FakeClock();
  }

  private BackoffDecider.Builder builder(String name) {
    return new BackoffDecider.Builder(name)
        .withSeedSize(1)
        .withRequestWindow(Amount.of(10L, Time.SECONDS))
        .withBucketCount(100)
        .withClock(clock)
        .withRandom(random);
  }

  @After
  public void verify() {
    control.verify();
  }

  @Test
  public void testAllSuccess() {
    control.replay();

    BackoffDecider decider = builder(NAME).build();
    run(decider, 10, Result.SUCCESS, State.NORMAL);
  }

  @Test
  public void testAllFailures() {
    control.replay();

    BackoffDecider decider = builder(NAME).build();
    run(decider, 10, Result.FAILURE, State.BACKOFF);
  }

  @Test
  public void testSingleFailure() {
    control.replay();

    BackoffDecider decider = builder(NAME).build();
    run(decider, 10, Result.SUCCESS, State.NORMAL);
    run(decider, 1, Result.FAILURE, State.NORMAL);
  }

  @Test
  public void testBelowThreshold() {
    control.replay();

    BackoffDecider decider = builder(NAME).withTolerateFailureRate(0.5).build();
    run(decider, 5, Result.SUCCESS, State.NORMAL);
    run(decider, 5, Result.FAILURE, State.NORMAL);
  }

  @Test
  public void testAtThreshold() {
    control.replay();

    BackoffDecider decider = builder(NAME).withTolerateFailureRate(0.49).build();
    run(decider, 51, Result.SUCCESS, State.NORMAL);
    run(decider, 49, Result.FAILURE, State.NORMAL);
  }

  @Test
  public void testAboveThreshold() {
    control.replay();

    BackoffDecider decider = builder(NAME).withTolerateFailureRate(0.49).build();
    run(decider, 51, Result.SUCCESS, State.NORMAL);
    run(decider, 49, Result.FAILURE, State.NORMAL);
    run(decider, 1, Result.FAILURE, State.BACKOFF);
  }

  @Test
  public void testRecoversFromBackoff() {
    // Backoff for the single request during the recovery period.
    expect(random.nextDouble()).andReturn(1d);

    control.replay();

    BackoffDecider decider = builder(NAME).build();
    decider.addFailure();
    assertThat(decider.shouldBackOff(), is(true));

    // Enter recovery period.
    clock.waitFor(101);
    assertThat(decider.shouldBackOff(), is(true));

    // Enter normal period.
    clock.waitFor(101);
    assertThat(decider.shouldBackOff(), is(false));
  }

  @Test
  public void testLinearRecovery() {
    for (int i = 0; i < 10; i++) {
      expect(random.nextDouble()).andReturn(0.1 * i + 0.01);  // Above threshold - back off.
      expect(random.nextDouble()).andReturn(0.1 * i - 0.01);  // Below threshold - allow request.
    }

    control.replay();

    BackoffDecider decider = builder(NAME).build();
    decider.addFailure(); // Moves into backoff state.
    assertThat(decider.shouldBackOff(), is(true));

    // Enter recovery period.
    clock.waitFor(101);

    // Step linearly through recovery period (100 ms).
    for (int i = 0; i < 10; i++) {
      clock.waitFor(10);
      assertThat(decider.shouldBackOff(), is(true));
      assertThat(decider.shouldBackOff(), is(false));
    }
  }

  @Test
  public void testExponentialBackoff() {
    // Don't back off during recovery period.
    expect(random.nextDouble()).andReturn(0d).atLeastOnce();

    control.replay();

    BackoffDecider decider = builder(NAME).build();
    List<Integer> backoffDurationsMs = Arrays.asList(
        100, 200, 400, 800, 1600, 3200, 6400, 10000, 10000);

    assertThat(decider.shouldBackOff(), is(false));

    // normal -> backoff
    decider.addFailure();
    assertThat(decider.shouldBackOff(), is(true));

    for (int backoffDurationMs : backoffDurationsMs) {
      assertThat(decider.shouldBackOff(), is(true));

      // backoff -> recovery
      clock.waitFor(backoffDurationMs + 1);
      assertThat(decider.shouldBackOff(), is(false));

      // recovery -> backoff
      decider.addFailure();
    }
  }

  @Test
  public void testRequestsExpire() {
    control.replay();

    BackoffDecider decider = builder(NAME).build();
    run(decider, 10, Result.SUCCESS, State.NORMAL);
    run(decider, 10, Result.FAILURE, State.NORMAL);
    assertThat(decider.shouldBackOff(), is(false));

    // Depends on request window of 10 seconds, with 100 buckets.
    clock.waitFor(10000);
    run(decider, 1, Result.SUCCESS, State.NORMAL);
    assertThat(decider.shouldBackOff(), is(false));
    assertThat(decider.requests.totalRequests, is(21L));
    assertThat(decider.requests.totalFailures, is(10L));

    // Requests should have decayed out of the window.
    clock.waitFor(101);
    run(decider, 1, Result.SUCCESS, State.NORMAL);
    assertThat(decider.shouldBackOff(), is(false));
    assertThat(decider.requests.totalRequests, is(2L));
    assertThat(decider.requests.totalFailures, is(0L));
  }

  @Test
  public void testAllBackendsDontBackoff() {
    // Back off for all requests during recovery period.
    expect(random.nextDouble()).andReturn(1d); // decider2 in recovery.
    expect(random.nextDouble()).andReturn(0d); // decider3 in recovery.

    control.replay();

    Set<BackoffDecider> group = Sets.newHashSet();
    BackoffDecider decider1 = builder(NAME + 1).groupWith(group).build();
    BackoffDecider decider2 = builder(NAME + 2).groupWith(group).build();
    BackoffDecider decider3 = builder(NAME + 3).groupWith(group).build();

    // Two of three backends start backing off.
    decider1.addFailure();
    assertThat(decider1.shouldBackOff(), is(true));

    decider2.addFailure();
    assertThat(decider2.shouldBackOff(), is(true));

    // Since all but 1 backend is backing off, we switch out of backoff mode.
    assertThat(decider3.shouldBackOff(), is(false));
    decider3.addFailure();
    assertThat(decider1.shouldBackOff(), is(false));
    assertThat(decider2.shouldBackOff(), is(false));
    assertThat(decider3.shouldBackOff(), is(false));

    // Begin recovering one backend, others will return to recovery.
    decider1.addSuccess();
    assertThat(decider1.shouldBackOff(), is(false)); // Still thinks others are backing off.
    assertThat(decider2.shouldBackOff(), is(false)); // Realizes decider1 is up, moves to recovery.
    assertThat(decider2.shouldBackOff(), is(true));  // In recovery.
    assertThat(decider3.shouldBackOff(), is(false)); // Realizes 1 & 2 are up, moves to recovery.
    assertThat(decider3.shouldBackOff(), is(false));  // In recovery.
  }

  @Test
  public void testOneBackendDoesntAffectOthers() {
    control.replay();

    Set<BackoffDecider> group = Sets.newHashSet();
    BackoffDecider decider1 = builder(NAME + 1).groupWith(group).build();
    BackoffDecider decider2 = builder(NAME + 2).groupWith(group).build();
    BackoffDecider decider3 = builder(NAME + 3).groupWith(group).build();

    // One backend starts failing.
    run(decider1, 10, Result.SUCCESS, State.NORMAL);
    run(decider2, 10, Result.SUCCESS, State.NORMAL);
    run(decider3, 10, Result.FAILURE, State.BACKOFF);

    // Other backends should remain normal.
    run(decider1, 10, Result.SUCCESS, State.NORMAL);
    run(decider2, 10, Result.SUCCESS, State.NORMAL);
  }

  @Test
  public void testPreventsBackoffFlapping() {
    // Permit requests during the backoff period.
    expect(random.nextDouble()).andReturn(0d).atLeastOnce();

    control.replay();

    BackoffDecider decider = builder(NAME).build();

    // Simulate 20 threads being permitted to send a request.
    for (int i = 0; i < 20; i++) assertThat(decider.shouldBackOff(), is(false));

    // The first 4 threads succeed.
    for (int i = 0; i < 4; i++) decider.addSuccess();
    assertThat(decider.shouldBackOff(), is(false));

    // The next 6 fail, triggering backoff mode.
    for (int i = 0; i < 6; i++) decider.addFailure();
    assertThat(decider.shouldBackOff(), is(true));

    // The next 10 succeed, but we are already backing off...so we should not move out of backoff.
    for (int i = 0; i < 10; i++) decider.addSuccess();
    assertThat(decider.shouldBackOff(), is(true));

    // Attempt to push the decider into a higher backoff period.
    for (int i = 0; i < 10; i++) decider.addFailure();

    // Verify that the initial backoff period is in effect.
    clock.waitFor(101);
    assertThat(decider.shouldBackOff(), is(false));
  }

  private enum Result {
    SUCCESS, FAILURE
  }

  private enum State {
    BACKOFF, NORMAL
  }

  private void run(BackoffDecider decider, int numRequests, Result result, State state) {
    for (int i = 0; i < numRequests; i++) {
      if (result == Result.SUCCESS) {
        decider.addSuccess();
      } else {
        decider.addFailure();
      }

      boolean backingOff = state == State.BACKOFF;
      assertThat(decider.shouldBackOff(), is(backingOff));
    }
  }
}
