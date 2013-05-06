package com.twitter.common.metrics;

import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.twitter.common.base.MorePreconditions;

/**
 * A metric registry that is a 'child' of another metric registry.
 */
public class ScopedMetrics implements MetricRegistry {

  @VisibleForTesting static final String SCOPE_DELIMITER = ".";

  private final String name;
  private final MetricRegistry parentScope;

  /**
   * Creates a new scoped metric registry.
   * When a gauge is registered with this registry, it will be passed directly to the parent,
   * with the scope name applied to the gauge name.
   *
   * @param name Name of this scope.
   * @param parentScope Parent scope to register gauges with.
   */
  @VisibleForTesting
  ScopedMetrics(String name, MetricRegistry parentScope) {
    this.name = MorePreconditions.checkNotBlank(name);
    this.parentScope = Preconditions.checkNotNull(parentScope);
  }

  @Override
  public MetricRegistry scope(String scopeName) {
    return new ScopedMetrics(scopeName, this);
  }

  private String scopeName(String metricName) {
    return name + SCOPE_DELIMITER + metricName;
  }

  @Override
  public <T extends Number> void register(final Gauge<T> gauge) {
    final String scopedName = scopeName(gauge.getName());
    parentScope.register(new AbstractGauge<T>(scopedName) {
      @Override public T read() {
        return gauge.read();
      }
    });
  }

  @Override
  public AtomicLong registerLong(String gaugeName) {
    return parentScope.registerLong(scopeName(gaugeName));
  }
}
