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

/**
 * Provides gauge composition to track per-event statistics, where a variable {@code value} is
 * associated with each event.
 * This will export a total of five metrics:
 * <ul>
 *   <li>value counter</li>
 *   <li>event counter</li>
 *   <li>value per-second rate</li>
 *   <li>event per-second rate</li>
 *   <li>ratio-of-rates: rate(value) / rate(event)</li>
 * </ul>
 *
 */
public class Events {

  private final Counter totalEvents;
  private final Counter totalValue;

  /**
   * Creates a new event composition using custom event and value metric names.
   *
   * @param eventMetricName Event metric name to export.
   * @param valueMetricName Value metric name to export.
   * @param registry Registry to associate metrics with.
   */
  public Events(String eventMetricName, String valueMetricName,
      MetricRegistry registry) {
    totalEvents = registry.createCounter(eventMetricName);
    totalValue = registry.createCounter(valueMetricName);
  }

  /**
   * Accumulates a value and increments the event counter.
   *
   * @param value Value to accumulate.
   */
  public synchronized void accumulate(long value) {
    totalEvents.increment();
    totalValue.add(value);
  }
}
