package com.twitter.common.metrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import com.twitter.common.util.Clock;

/**
 * A sampler that associates a {@link MetricProvider} with multiple {@link MetricListener}s.
 */
class MetricSampler implements Runnable {

  private final MetricProvider metricProvier;
  private final List<MetricListener> listeners;
  private final Clock clock;
  private final Events events;

  /**
   * Creates a new metric sampler.
   *
   * @param metricProvider Source of metric samples.
   * @param listeners Sample sinks.
   * @param registry Registry to export sampling-related metrics to.
   * @param clock Clock for timing metric sample duration.
   */
  @VisibleForTesting
  MetricSampler(MetricProvider metricProvider, Iterable<MetricListener> listeners,
      MetricRegistry registry, Clock clock) {
    this.metricProvier = Preconditions.checkNotNull(metricProvider);
    this.listeners = new CopyOnWriteArrayList<MetricListener>(ImmutableList.copyOf(listeners));
    this.clock = Preconditions.checkNotNull(clock);
    Preconditions.checkNotNull(registry);
    this.events = new Events("metric_samples", "metric_sample_delay", registry);
  }

  public void addListener(MetricListener listener) {
    listeners.add(listener);
  }

  @Override
  public void run() {
    long startNanos = clock.nowNanos();
    Map<String, Number> metrics = metricProvier.sample();
    events.accumulate(clock.nowNanos() - startNanos);
    for (MetricListener listener : listeners) {
      listener.updateStats(metrics);
    }
  }
}
