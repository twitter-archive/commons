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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.twitter.common.base.MorePreconditions;

/**
 * A metric registry that is a 'child' of another metric registry.
 */
public class ScopedRegistry implements MetricRegistry {

  @VisibleForTesting static final String SCOPE_DELIMITER = ".";

  private final MetricRegistry parent;
  private final String name;


  /**
   * Creates a new scoped metric registry.
   * When a gauge is registered with this registry, it will be passed directly to the parent,
   * with the scope name applied to the gauge name.
   *
   * @param name Name of this scope.
   * @param parent Parent scope to register gauges with.
   */
  @VisibleForTesting
  ScopedRegistry(String name, MetricRegistry parent) {
    this.name = MorePreconditions.checkNotBlank(name);
    this.parent = Preconditions.checkNotNull(parent);
  }

  @Override
  public MetricRegistry scope(String scopeName) {
    return new ScopedRegistry(scopeName, this);
  }

  private String scopeName(String metricName) {
    return name + SCOPE_DELIMITER + metricName;
  }

  @Override
  public <T extends Number> void register(final Gauge<T> gauge) {
    final String scopedName = scopeName(gauge.getName());
    parent.register(new AbstractGauge<T>(scopedName) {
      @Override public T read() {
        return gauge.read();
      }
    });
  }

  @Override
  public Counter createCounter(String gaugeName) {
    return parent.createCounter(scopeName(gaugeName));
  }

  @Override
  public Counter registerCounter(String gaugeName) {
    return createCounter(gaugeName);
  }
}
