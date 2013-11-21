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

import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import com.twitter.jsr166e.LongAdder;

/**
 * Root metric registry.
 */
public class Metrics implements MetricRegistry, MetricProvider {

  private static final Metrics ROOT = new Metrics();

  private final Map<String, Gauge<?>> metrics = Maps.newConcurrentMap();


  @VisibleForTesting
  Metrics() {
    // Package private.
  }

  /**
   * Create a new Metrics detached from the static root.
   *
   * @return the detached metric registry.
   */
  public static Metrics createDetached() {
    return new Metrics();
  }

  /**
   * Returns a handle to the root metric registry.
   *
   * @return Root metric registry.
   */
  public static Metrics root() {
    return ROOT;
  }

  @Override
  public MetricRegistry scope(String name) {
    return new ScopedRegistry(name, this);
  }

  @Override
  public <T extends Number> void register(Gauge<T> gauge) {
    // TODO(wfarner): Define a policy for handling collisions.
    metrics.put(gauge.getName(), gauge);
  }

  @Override
  public Counter createCounter(String name) {
    final LongAdder adder = new LongAdder();
    register(new AbstractGauge<Long>(name) {
      @Override public Long read() {
        return adder.sum();
      }
    });
    return new Counter() {
      public void increment() {
        adder.increment();
      }
      public void add(long n) {
        adder.add(n);
      }
    };
  }

  @Override
  public Map<String, Number> sample() {
    ImmutableMap.Builder<String, Number> samples = ImmutableMap.builder();
    for (Map.Entry<String, Gauge<?>> metric : metrics.entrySet()) {
      samples.put(metric.getKey(), metric.getValue().read());
    }
    return samples.build();
  }
}
