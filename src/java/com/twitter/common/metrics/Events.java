package com.twitter.common.metrics;

import java.util.concurrent.atomic.AtomicLong;

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

  private final AtomicLong totalEvents;
  private final AtomicLong totalValue;

  /**
   * Creates a new event composition using custom event and value metric names.
   *
   * @param eventMetricName Event metric name to export.
   * @param valueMetricName Value metric name to export.
   * @param registry Registry to associate metrics with.
   */
  public Events(String eventMetricName, String valueMetricName,
      MetricRegistry registry) {
    totalEvents = registry.registerLong(eventMetricName);
    totalValue = registry.registerLong(valueMetricName);
    Rate eventRate = Rate.of(eventMetricName, totalEvents);
    Rate valueRate = Rate.of(valueMetricName, totalValue);
    registry.register(eventRate);
    registry.register(valueRate);
    registry.register(Ratio.of(eventRate, valueRate));
  }

  /**
   * Accumulates a value and increments the event counter.
   *
   * @param value Value to accumulate.
   */
  public synchronized void accumulate(long value) {
    totalEvents.incrementAndGet();
    totalValue.addAndGet(value);
  }
}
