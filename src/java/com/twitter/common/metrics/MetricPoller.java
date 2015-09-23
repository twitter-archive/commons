// =================================================================================================
// Copyright 2013 Twitter, Inc.
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

package com.twitter.common.metrics;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.concurrent.ExecutorServiceShutdown;

/**
 * A fixed-rate poller that triggers a {@link MetricSampler}.
 */
public class MetricPoller {

  private static final Logger LOG = Logger.getLogger(MetricPoller.class.getName());

  private static final Amount<Long, Time> SHUTDOWN_GRACE_PERIOD = Amount.of(2L, Time.SECONDS);

  private final MetricSampler sampler;
  private final ScheduledExecutorService executor;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  /**
   * Creates a new metric poller.
   *
   * @param sampler Sampler to fetch metric values from.
   * @param executor Executor service to run the sampler with.
   */
  public MetricPoller(MetricSampler sampler, ScheduledExecutorService executor) {
    this.sampler = Preconditions.checkNotNull(sampler);
    this.executor = Preconditions.checkNotNull(executor);
  }

  /**
   * Initiates the poller.
   * The poller may only be started once.  If an attempt is made to start a poller when it is
   * already started, {@link IllegalStateException} will be thrown.
   *
   * @param pollInterval Fixed poll rate.
   */
  public void start(Amount<Long, Time> pollInterval) {
    Preconditions.checkNotNull(pollInterval);
    Preconditions.checkArgument(pollInterval.getValue() > 0, "Poll interval must be positive");
    Preconditions.checkState(started.compareAndSet(false, true), "Poller is already started.");

    long pollIntervalMs = pollInterval.as(Time.MILLISECONDS);
    executor.scheduleAtFixedRate(sampler, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
  }

  /**
   * Stops the poller.
   * If the poller is already stopped, or is in the process of being stopped, subsequent calls will
   * throw {@link IllegalStateException}.
   */
  public void stop() {
    if (stopped.compareAndSet(false, true)) {
      new ExecutorServiceShutdown(executor, SHUTDOWN_GRACE_PERIOD).execute();
    } else {
      LOG.warning("Poller is already stopped, subsequent calls ignored.");
    }
  }
}
