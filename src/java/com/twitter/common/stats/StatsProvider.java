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

import com.google.common.base.Supplier;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A minimal interface to a Stats repository.
 *
 * @author John Sirois
 */
public interface StatsProvider {

  /**
   * Creates and exports a counter for tracking.
   *
   * @param name The name to export the stat with.
   * @return A reference to the counter that will be tracked for incrementing.
   */
  AtomicLong makeCounter(String name);

  /**
   * Exports a read-only value for tracking.
   *
   * @param name The name of the variable to export.
   * @param gauge The supplier of the instantaneous values to export.
   * @param <T> The type of number exported by the variable.
   * @return A reference to the stat that was stored.
   */
  <T extends Number> Stat<T> makeGauge(String name, Supplier<T> gauge);

  /**
   * Gets a stats provider that does not track stats in an internal time series repository.
   * The stored variables will only be available as instantaneous values.
   *
   * @return A stats provider that creates untracked stats.
   */
  StatsProvider untracked();

  /**
   * A stat for tracking service requests.
   */
  interface RequestTimer {

    /**
     * Accumulates a request and its latency.
     *
     * @param latencyMicros The elapsed time required to complete the request.
     */
    void requestComplete(long latencyMicros);

    /**
     * Accumulates the error counter and the request counter.
     */
    void incErrors();

    /**
     * Accumulates the reconnect counter.
     */
    void incReconnects();

    /**
     * Accumulates the timeout counter.
     */
    void incTimeouts();
  }

  /**
   * Creates and exports a sets of stats that allows for typical rROC request tracking.
   *
   * @param name The name to export the stat with.
   * @return A reference to the request timer that can be used to track RPCs.
   */
  RequestTimer makeRequestTimer(String name);
}
