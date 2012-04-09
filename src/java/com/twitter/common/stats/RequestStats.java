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

package com.twitter.common.stats;

import com.twitter.common.stats.StatsProvider.RequestTimer;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A class to represent the statistics associated with a client connection to an external service.
 * Tracks request latency/rate, and error rate.
 *
 * @author William Farner
 */
public class RequestStats implements RequestTimer {

  private static final float DEFAULT_SAMPLE_PERCENT = 10;
  private static final double[] DEFAULT_PERCENTILES = {10, 50, 90, 99, 99.9, 99.99};

  private final SlidingStats requests;
  private final Percentile<Long> percentile;

  private final AtomicLong errors;
  private final AtomicLong reconnects;
  private final AtomicLong timeouts;

  /**
   * Creates a new request statistics object, using the default percentiles and sampling rate.
   *
   * @param name The unique name for this request type.
   */
  public RequestStats(String name) {
    this(name, new Percentile<Long>(name, DEFAULT_SAMPLE_PERCENT, DEFAULT_PERCENTILES));
  }

  /**
   * Creates a new request statistics object using a custom percentile tracker.
   *
   * @param name The unique name for this request type.
   * @param percentile The percentile tracker, or {@code null} to disable percentile tracking.
   */
  public RequestStats(String name, @Nullable Percentile<Long> percentile) {
    requests = new SlidingStats(name + "_requests", "micros");
    this.percentile = percentile;
    errors = Stats.exportLong(name + "_errors");
    reconnects = Stats.exportLong(name + "_reconnects");
    timeouts = Stats.exportLong(name + "_timeouts");
    Rate<AtomicLong> requestsPerSec =
        Rate.of(name + "_requests_per_sec", requests.getEventCounter()).build();
    Stats.export(Ratio.of(name + "_error_rate",
        Rate.of(name + "_errors_per_sec", errors).build(), requestsPerSec));
    Rate<AtomicLong> timeoutsPerSec = Rate.of(name + "_timeouts_per_sec", timeouts).build();
    Stats.export(timeoutsPerSec);
    Stats.export(Ratio.of(name + "_timeout_rate", timeoutsPerSec, requestsPerSec));
  }

  public SlidingStats getSlidingStats() {
    return requests;
  }

  public AtomicLong getErrorCounter() {
    return errors;
  }

  public AtomicLong getReconnectCounter() {
    return reconnects;
  }

  public AtomicLong getTimeoutCounter() {
    return timeouts;
  }

  public Percentile<Long> getPercentile() {
    return percentile;
  }

  /**
   * Accumulates a request and its latency.
   *
   * @param latencyMicros The elapsed time required to complete the request.
   */
  public void requestComplete(long latencyMicros) {
    requests.accumulate(latencyMicros);
    if (percentile != null) percentile.record(latencyMicros);
  }

  /**
   * Accumulates the error counter and the request counter.
   */
  public void incErrors() {
    requestComplete(0);
    errors.incrementAndGet();
  }

  /**
   * Accumulates the error counter, the request counter and the request latency.
   *
   * @param latencyMicros The elapsed time before the error occurred.
   */
  public void incErrors(long latencyMicros) {
    requestComplete(latencyMicros);
    errors.incrementAndGet();
  }

  /**
   * Accumulates the reconnect counter.
   */
  public void incReconnects() {
    reconnects.incrementAndGet();
  }

  /**
   * Accumulates the timtout counter.
   */
  public void incTimeouts() {
    timeouts.incrementAndGet();
  }

  public long getErrorCount() {
    return errors.get();
  }

  public long getReconnectCount() {
    return reconnects.get();
  }

  public long getTimeoutCount() {
    return timeouts.get();
  }
}
