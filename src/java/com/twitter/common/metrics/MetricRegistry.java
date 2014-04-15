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
 * A registry that maintains a collection of metrics.
 */
public interface MetricRegistry {

  /**
   * Returns or creates a sub-scope of this metric registry.
   *
   * @param name Name for the sub-scope.
   * @return A possibly-new metric registry, whose metrics will be 'children' of this scope.
   */
  MetricRegistry scope(String name);

  /**
   * Registers a new gauge.
   *
   * @deprecated Please use registerGauge instead.
   * @param gauge Gauge to register.
   * @param <T> Number type of the gauge's values.
   */
  @Deprecated
  <T extends Number> void register(Gauge<T> gauge);

  /**
   * Registers a new gauge.
   *
   * @param gauge Gauge to register.
   * @param <T> Number type of the gauge's values.
   * @return the Gauge created.
   */
  <T extends Number> Gauge<T> registerGauge(Gauge<T> gauge);

  /**
   * Unregisters a gauge from the registry.
   *
   * @param gauge Gauge to unregister.
   * @return true if the gauge was successfully unregistered, false otherwise.
   */
  boolean unregister(Gauge<?> gauge);

  /**
   * Creates and returns a {@link Counter} that can be incremented.
   *
   * @param name Name to associate with the counter.
   * @return Counter (initialized to zero) to increment the value.
   */
  Counter createCounter(String name);

  /**
   * Creates a counter and returns an {@link Counter} that can be incremented.
   * @deprecated Please use createCounter instead.
   * @param name Name to associate with the gauge.
   * @return Counter (initialized to zero) to increment the value.
   */
  @Deprecated
  Counter registerCounter(String name);

  /**
   * Unregisters a counter from the registry.
   * @param counter Counter to unregister.
   * @return true if the counter was successfully unregistered, false otherwise.
   */
  boolean unregister(Counter counter);

  /**
   * Create a HistogramInterface (with default parameters).
   * @return the newly created histogram.
   */
  HistogramInterface createHistogram(String name);

  /**
   * Register an HistogramInterface into the Metrics registry.
   * Useful when you want to create custom histogram (e.g. with better precision).
   * @return the Histogram you registered for chaining purposes.
   */
  HistogramInterface registerHistogram(HistogramInterface histogram);

  /**
   * Unregisters an histogram from the registry.
   * @param histogram Histogram to unregister.
   * @return true if the histogram was successfully unregistered, false otherwise.
   */
  boolean unregister(HistogramInterface histogram);

  /**
   * Convenient method that unregister any metric (identified by its name) from the registry.
   * @param name Name of metric to unregister.
   * @return true if the metric was successfully unregistered, false otherwise.
   */
  boolean unregister(String name);
}
