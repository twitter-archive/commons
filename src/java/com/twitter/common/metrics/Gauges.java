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

import java.util.Collection;

import com.google.common.base.Supplier;

/**
 * Convenience functions for working with {@link Gauge}s.
 */
public final class Gauges {

  private Gauges() {
    // Utility class.
  }

  /**
   * Creates a supplier that serves as an accessor for gauge values.
   *
   * @param gauge Gauge to turn into a supplier.
   * @return Supplier of values from {@code gauge}.
   */
  public static Supplier<Number> asSupplier(final Gauge<?> gauge) {
    return new Supplier<Number>() {
      @Override public Number get() {
        return gauge.read();
      }
    };
  }

  /**
   * Registers the size of a collection as a gauge.
   *
   * @param registry Registry to register the gauge with.
   * @param name Name for the gauge.
   * @param collection Collection to register size of.
   */
  public static void registerSize(MetricRegistry registry, String name,
      final Collection<?> collection) {
    registry.register(new AbstractGauge<Integer>(name) {
      @Override public Integer read() {
        return collection.size();
      }
    });
  }
}
