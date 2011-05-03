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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.Stats;
import com.twitter.common.stats.StatsProvider;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Handles logic for deciding whether to back off from calls to a backend.
 *
 * This works by offering a guard method {@link #shouldBackOff()}, which instructs the caller
 * whether they should avoid making the call.  The backoff logic will maintain statistics about
 * the failure rate, and push into a backoff state (silent period) when the failure rate exceeds
 * the configured threshold.  At the end of the quiet period, a recovery state will be entered,
 * during which the decider will allow traffic to ramp back up to full capacity.
 *
 * The expected use case looks something like this:
 *
 * <pre>
 * void sendRequestGuarded() {
 *   if (!decider.shouldBackOff()) {
 *     boolean success = sendRequestUnguarded();
 *     if (success) {
 *       decider.addSuccess();
 *     } else {
 *       decider.addFailure();
 *     }
 *   }
 * }
 * </pre>
 *
 * @author William Farner
 */
public class BackoffDecider {
  private static final Logger LOG = Logger.getLogger(BackoffDecider.class.getName());

  // The group that this decider is a part of.
  private final Iterable<BackoffDecider> deciderGroup;

  private final TimedStateMachine stateMachine;

  private final String name;

  private final double toleratedFailureRate;

  @VisibleForTesting final RequestWindow requests;

  // Used to calculate backoff durations when in backoff state.
  private final BackoffStrategy strategy;

  private final Amount<Long, Time> recoveryPeriod;
  private long previousBackoffPeriodNs = 0;

  // Used for random selection during recovery period.
  private final Random random;

  private final Clock clock;
  private final AtomicLong backoffs;
  private final RecoveryType recoveryType;

  /**
   * Different types of recovery mechanisms to use after exiting the backoff state.
   */
  public static enum RecoveryType {
    // Randomly allows traffic to flow through, with a linearly-ascending probability.
    RANDOM_LINEAR,
    // Allows full traffic capacity to flow during the recovery period.
    FULL_CAPACITY
  }

  private BackoffDecider(String name, int seedSize, double toleratedFailureRate,
      @Nullable Iterable<BackoffDecider> deciderGroup, BackoffStrategy strategy,
      @Nullable Amount<Long, Time> recoveryPeriod,
      long requestWindowNs, int numBuckets, RecoveryType recoveryType, StatsProvider statsProvider,
      Random random, Clock clock) {
    MorePreconditions.checkNotBlank(name);
    Preconditions.checkArgument(seedSize > 0);
    Preconditions.checkArgument(toleratedFailureRate >= 0 && toleratedFailureRate < 1.0);
    Preconditions.checkNotNull(strategy);
    Preconditions.checkArgument(recoveryPeriod == null || recoveryPeriod.getValue() > 0);
    Preconditions.checkArgument(requestWindowNs > 0);
    Preconditions.checkArgument(numBuckets > 0);
    Preconditions.checkNotNull(recoveryType);
    Preconditions.checkNotNull(statsProvider);
    Preconditions.checkNotNull(random);
    Preconditions.checkNotNull(clock);

    this.name = name;
    this.toleratedFailureRate = toleratedFailureRate;
    this.deciderGroup = deciderGroup;
    this.strategy = strategy;
    this.recoveryPeriod = recoveryPeriod;
    this.recoveryType = recoveryType;

    this.random = random;
    this.clock = clock;

    this.backoffs = statsProvider.makeCounter(name + "_backoffs");
    this.requests = new RequestWindow(requestWindowNs, numBuckets, seedSize);

    this.stateMachine = new TimedStateMachine(name);
  }

  /**
   * Checks whether the caller should back off and if not then returns immediately; otherwise the
   * method blocks until it is safe for the caller to proceed without backing off further based on
   * all data available at the time of this call.
   *
   * @return the amount of time in nanoseconds spent awaiting backoff
   * @throws InterruptedException if the calling thread was interrupted while backing off
   */
  public long awaitBackoff() throws InterruptedException {
    if (shouldBackOff()) {
      long backoffTimeMs = stateMachine.getStateRemainingMs();

      if (backoffTimeMs > 0) {
        // Wait without holding any external locks.
        Object waitCondition = new Object();
        synchronized (waitCondition) {
          waitCondition.wait(backoffTimeMs);
        }
        return backoffTimeMs;
      }
    }
    return 0;
  }

  /**
   * Checks whether this decider instructs the caller that it should back off from the associated
   * backend.  This is determined based on the response history for the backend as well as the
   * backoff state of the decider group (if configured).
   *
   * @return {@code true} if the decider is in backoff mode, otherwise {@code false}.
   */
  @SuppressWarnings("fallthrough")
  public synchronized boolean shouldBackOff() {

    boolean preventRequest;
    switch (stateMachine.getState()) {
      case NORMAL:
        preventRequest = false;
        break;

      case BACKOFF:
        if (deciderGroup != null && allOthersBackingOff()) {
          LOG.info("Backends in group with " + name + " down, forcing back up.");
          stateMachine.transitionUnbounded(State.FORCED_NORMAL);
          return false;
        } else if (stateMachine.isStateExpired()) {
          long recoveryPeriodNs = recoveryPeriod == null ? stateMachine.getStateDurationNs()
              : recoveryPeriod.as(Time.NANOSECONDS);

          // The silent period has expired, move to recovery state (and drop to its case block).
          stateMachine.transition(State.RECOVERY, recoveryPeriodNs);
          LOG.info(String.format("%s recovering for %s ms", name,
              Amount.of(recoveryPeriodNs, Time.NANOSECONDS).as(Time.MILLISECONDS)));
        } else {
          preventRequest = true;
          break;
        }

      case RECOVERY:
        if (deciderGroup != null && allOthersBackingOff()) {
          return false;
        } else if (stateMachine.isStateExpired()) {
          // We have reached the end of the recovery period, return to normal.
          stateMachine.transitionUnbounded(State.NORMAL);
          previousBackoffPeriodNs = 0;
          preventRequest = false;
        } else {
          switch (recoveryType) {
            case RANDOM_LINEAR:
              // In the recovery period, allow request rate to return linearly to the full load.
              preventRequest = random.nextDouble() > stateMachine.getStateFractionComplete();
              break;
            case FULL_CAPACITY:
              preventRequest = false;
              break;
            default:
              throw new IllegalStateException("Unhandled recovery type " + recoveryType);
          }
        }

        break;

      case FORCED_NORMAL:
        if (!allOthersBackingOff()) {
          // We were in forced normal state, but at least one other backend is up, try recovering.
          stateMachine.transition(State.RECOVERY, stateMachine.getStateDurationNs());
          preventRequest = false;
        } else {
          preventRequest = true;
        }

        break;

      default:
        LOG.severe("Unrecognized state: " + stateMachine.getState());
        preventRequest = false;
    }

    if (preventRequest) {
      backoffs.incrementAndGet();
    }
    return preventRequest;
  }

  private boolean allOthersBackingOff() {
    // Search for another decider that is not backing off.
    for (BackoffDecider decider : deciderGroup) {
      State deciderState = decider.stateMachine.getState();
      boolean inBackoffState = deciderState == State.BACKOFF || deciderState == State.FORCED_NORMAL;
      if ((decider != this) && !inBackoffState) {
        return false;
      }
    }

    return true;
  }

  /**
   * Records a failed request to the backend.
   */
  public void addFailure() {
    addResult(false);
  }

  /**
   * Records a successful request to the backend.
   */
  public void addSuccess() {
    addResult(true);
  }

  /**
   * Transitions the state to BACKOFF and logs a message appropriately if it is doing so because of high fail rate
   * or by force.
   *
   * @param failRate rate of request failures on this host.
   * @param force if {@code true}, forces the transition to BACKOFF. Typically used in cases when the host
   * was not found to be alive by LiveHostChecker.
   */
  public synchronized void transitionToBackOff(double failRate, boolean force) {
    long prevBackoffMs = Amount.of(previousBackoffPeriodNs, Time.NANOSECONDS)
        .as(Time.MILLISECONDS);

    long backoffPeriodNs = Amount.of(strategy.calculateBackoffMs(prevBackoffMs), Time.MILLISECONDS)
        .as(Time.NANOSECONDS);
    if (!force) {
      LOG.info(String.format("%s failure rate at %g, backing off for %s ms", name,failRate,
          Amount.of(backoffPeriodNs, Time.NANOSECONDS).as(Time.MILLISECONDS)));
    } else {
      LOG.info(String.format("%s forced to back off for %s ms", name,
          Amount.of(backoffPeriodNs, Time.NANOSECONDS).as(Time.MILLISECONDS)));
    }
    stateMachine.transition(State.BACKOFF, backoffPeriodNs);
    previousBackoffPeriodNs = backoffPeriodNs;
  }

  @SuppressWarnings("fallthrough")
  private synchronized void addResult(boolean success) {
    // Disallow statistics updating if we are in backoff state.
    if (stateMachine.getState() == State.BACKOFF) {
      return;
    }

    requests.addResult(success);
    double failRate = requests.getFailureRate();
    boolean highFailRate = requests.isSeeded() && (failRate > toleratedFailureRate);

    switch (stateMachine.getState()) {
      case NORMAL:
        if (!highFailRate) {
          // No-op.
          break;
        } else {
          // Artificially move into recovery state (by falling through) with a zero-duration
          // time window, to trigger the initial backoff period.
          stateMachine.setStateDurationNs(0);
        }

      case RECOVERY:
        if (highFailRate) {
          // We were trying to recover, and the failure rate is still too high.  Go back to
          // backoff state for a longer duration.
          requests.reset();

          // transition the state machine to BACKOFF state, due to high fail rate.
          transitionToBackOff(failRate, false);
        } else {
          // Do nothing.  We only exit the recovery state by expiration.
        }
        break;

      case FORCED_NORMAL:
        if (!highFailRate) {
          stateMachine.transition(State.RECOVERY, stateMachine.getStateDurationNs());
        }
        break;

      case BACKOFF:
        throw new IllegalStateException("Backoff state may only be exited by expiration.");
    }
  }

  /**
   * Creates a builder object.
   *
   * @param name Name for the backoff decider to build.
   * @return A builder.
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Builder class to configure a BackoffDecider.
   *
   * The builder allows for customization of many different parameters to the BackoffDecider, while
   * defining defaults wherever possible.  The following defaults are used:
   *
   * <ul>
   * <li> seed size - The number of requests to accumulate before a backoff will be considered.
   * 100
   *
   * <li> tolerated failure rate - Maximum failure rate before backing off.
   * 0.5
   *
   * <li> decider group - Group this decider is a part of, to prevent complete backend failure.
   * null (disabled)
   *
   * <li> strategy - Used to calculate subsequent backoff durations.
   * TruncatedBinaryBackoff, initial 100 ms, max 10s
   *
   * <li> recovery period - Fixed recovery period while ramping traffic back to full capacity..
   * null (use last backoff period)
   *
   * <li> request window - Duration of the sliding window of requests to track statistics for.
   * 10 seconds
   *
   * <li> num buckets - The number of time slices within the request window, for stat expiration.
   *               The sliding request window advances in intervals of request window / num buckets.
   * 100
   *
   * <li> recovery type - Defines behavior during the recovery period, and how traffic is permitted.
   * random linear
   *
   * <li> stat provider - The stats provider to export statistics to.
   * Stats.STATS_PROVIDER
   * </ul>
   *
   */
  public static class Builder {
    private String name;
    private int seedSize = 100;
    private double toleratedFailureRate = 0.5;
    private Set<BackoffDecider> deciderGroup = null;
    private BackoffStrategy strategy = new TruncatedBinaryBackoff(
        Amount.of(100L, Time.MILLISECONDS), Amount.of(10L, Time.SECONDS));
    private Amount<Long, Time> recoveryPeriod = null;
    private long requestWindowNs = Amount.of(10L, Time.SECONDS).as(Time.NANOSECONDS);
    private int numBuckets = 100;
    private RecoveryType recoveryType = RecoveryType.RANDOM_LINEAR;
    private StatsProvider statsProvider = Stats.STATS_PROVIDER;
    private Random random = Random.Util.newDefaultRandom();
    private Clock clock = Clock.SYSTEM_CLOCK;

    Builder(String name) {
      this.name = name;
    }

    /**
     * Sets the number of requests that must be accumulated before the error rate will be
     * calculated.  This improves the genesis problem where the first few requests are errors,
     * causing flapping in and out of backoff state.
     *
     * @param seedSize Request seed size.
     * @return A reference to the builder.
     */
    public Builder withSeedSize(int seedSize) {
      this.seedSize = seedSize;
      return this;
    }

    /**
     * Sets the tolerated failure rate for the decider.  If the rate is exceeded for the time
     * window, the decider begins backing off.
     *
     * @param toleratedRate The tolerated failure rate (between 0.0 and 1.0, exclusive).
     * @return A reference to the builder.
     */
    public Builder withTolerateFailureRate(double toleratedRate) {
      this.toleratedFailureRate = toleratedRate;
      return this;
    }

    /**
     * Makes the decider a part of a group.  When a decider is a part of a group, it will monitor
     * the other deciders to ensure that all deciders do not back off at once.
     *
     * @param deciderGroup Group to make this decider a part of.  More deciders may be added to the
     *     group after this call is made.
     * @return A reference to the builder.
     */
    public Builder groupWith(Set<BackoffDecider> deciderGroup) {
      this.deciderGroup = deciderGroup;
      return this;
    }

    /**
     * Overrides the default backoff strategy.
     *
     * @param strategy Backoff strategy to use.
     * @return A reference to the builder.
     */
    public Builder withStrategy(BackoffStrategy strategy) {
      this.strategy = strategy;
      return this;
    }

    /**
     * Overrides the default recovery period behavior.  By default, the recovery period is equal
     * to the previous backoff period (which is equivalent to setting the recovery period to null
     * here).  A non-null value here will assign a fixed recovery period.
     *
     * @param recoveryPeriod Fixed recovery period.
     * @return A reference to the builder.
     */
    public Builder withRecoveryPeriod(@Nullable Amount<Long, Time> recoveryPeriod) {
      this.recoveryPeriod = recoveryPeriod;
      return this;
    }

    /**
     * Sets the time window over which to analyze failures.  Beyond the time window, request history
     * is discarded (and ignored).
     *
     * @param requestWindow The analysis time window.
     * @return A reference to the builder.
     */
    public Builder withRequestWindow(Amount<Long, Time> requestWindow) {
      this.requestWindowNs = requestWindow.as(Time.NANOSECONDS);
      return this;
    }

    /**
     * Sets the number of time slices that the decider will use to partition aggregate statistics.
     *
     * @param numBuckets Bucket count.
     * @return A reference to the builder.
     */
    public Builder withBucketCount(int numBuckets) {
      this.numBuckets = numBuckets;
      return this;
    }

    /**
     * Sets the recovery mechanism to use when in the recovery period.
     *
     * @param recoveryType The recovery mechanism to use.
     * @return A reference to the builder.
     */
    public Builder withRecoveryType(RecoveryType recoveryType) {
      this.recoveryType = recoveryType;
      return this;
    }

    /**
     * Sets the stats provider that statistics should be exported to.
     *
     * @param statsProvider Stats provider to use.
     * @return A reference to the builder.
     */
    public Builder withStatsProvider(StatsProvider statsProvider) {
      this.statsProvider = statsProvider;
      return this;
    }

    @VisibleForTesting public Builder withRandom(Random random) {
      this.random = random;
      return this;
    }

    @VisibleForTesting public Builder withClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Gets a reference to the built decider object.
     * @return A decider object.
     */
    public BackoffDecider build() {
      BackoffDecider decider =  new BackoffDecider(name, seedSize, toleratedFailureRate,
          deciderGroup, strategy, recoveryPeriod, requestWindowNs, numBuckets, recoveryType,
          statsProvider, random, clock);
      if (deciderGroup != null) deciderGroup.add(decider);
      return decider;
    }
  }

  private class TimeSlice {
    int requestCount = 0;
    int failureCount = 0;
    final long bucketStartNs;

    public TimeSlice() {
      bucketStartNs = clock.nowNanos();
    }
  }

  class RequestWindow {
    // These store the sum of the respective fields contained within buckets.  Doing so removes the
    // need to accumulate the counts within the buckets every time the backoff state is
    // recalculated.
    @VisibleForTesting long totalRequests = 0;
    @VisibleForTesting long totalFailures = 0;

    private final long durationNs;
    private final long bucketLengthNs;
    private final int seedSize;

    // Stores aggregate request/failure counts for time slices.
    private final Deque<TimeSlice> buckets = Lists.newLinkedList();

    RequestWindow(long durationNs, int bucketCount, int seedSize) {
      this.durationNs = durationNs;
      this.bucketLengthNs = durationNs / bucketCount;
      buckets.addFirst(new TimeSlice());
      this.seedSize = seedSize;
    }

    void reset() {
      totalRequests = 0;
      totalFailures = 0;
      buckets.clear();
      buckets.addFirst(new TimeSlice());
    }

    void addResult(boolean success) {
      maybeShuffleBuckets();
      buckets.peekFirst().requestCount++;
      totalRequests++;

      if (!success) {
        buckets.peekFirst().failureCount++;
        totalFailures++;
      }
    }

    void maybeShuffleBuckets() {
      // Check if the first bucket is still relevant.
      if (clock.nowNanos() - buckets.peekFirst().bucketStartNs >= bucketLengthNs) {

        // Remove old buckets.
        while (!buckets.isEmpty()
               && buckets.peekLast().bucketStartNs < clock.nowNanos() - durationNs) {
          TimeSlice removed = buckets.removeLast();
          totalRequests -= removed.requestCount;
          totalFailures -= removed.failureCount;
        }

        buckets.addFirst(new TimeSlice());
      }
    }

    boolean isSeeded() {
      return totalRequests >= seedSize;
    }

    double getFailureRate() {
      return totalRequests == 0 ? 0 : ((double) totalFailures) / totalRequests;
    }
  }

  private static enum State {
    NORMAL,        // All requests are being permitted.
    BACKOFF,       // Quiet period while waiting for backend to recover/improve.
    RECOVERY,      // Ramping period where an ascending fraction of requests is being permitted.
    FORCED_NORMAL  // All other backends in the group are backing off, so this one is forced normal.
  }
  private class TimedStateMachine {
    final StateMachine<State> stateMachine;

    private long stateEndNs;
    private long stateDurationNs;

    TimedStateMachine(String name) {
      stateMachine = StateMachine.<State>builder(name + "_backoff_state_machine")
          .addState(State.NORMAL, State.BACKOFF, State.FORCED_NORMAL)
          .addState(State.BACKOFF, State.RECOVERY, State.FORCED_NORMAL)
          .addState(State.RECOVERY, State.NORMAL, State.BACKOFF, State.FORCED_NORMAL)
          .addState(State.FORCED_NORMAL, State.RECOVERY)
          .initialState(State.NORMAL)
          .build();
    }

    State getState() {
      return stateMachine.getState();
    }

    void transitionUnbounded(State state) {
      stateMachine.transition(state);
    }

    void transition(State state, long durationNs) {
      transitionUnbounded(state);
      this.stateEndNs = clock.nowNanos() + durationNs;
      this.stateDurationNs = durationNs;
    }

    long getStateDurationNs() {
      return stateDurationNs;
    }

    long getStateDurationMs() {
      return Amount.of(stateDurationNs, Time.NANOSECONDS).as(Time.MILLISECONDS);
    }

    void setStateDurationNs(long stateDurationNs) {
      this.stateDurationNs = stateDurationNs;
    }

    long getStateRemainingNs() {
      return stateEndNs - clock.nowNanos();
    }

    long getStateRemainingMs() {
      return Amount.of(getStateRemainingNs(), Time.NANOSECONDS).as(Time.MILLISECONDS);
    }

    double getStateFractionComplete() {
      return 1.0 - ((double) getStateRemainingNs()) / stateDurationNs;
    }

    boolean isStateExpired() {
      return clock.nowNanos() > stateEndNs;
    }
  }
}
