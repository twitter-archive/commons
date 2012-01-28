package com.twitter.common.metrics;

import java.util.LinkedList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;

import com.twitter.common.collections.Pair;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

/**
 * Gauge that computes a windowed rate.
 */
public class Rate extends AbstractGauge<Double> {

  private static final Amount<Long, Time> ONE_SECOND = Amount.of(1L, Time.SECONDS);
  private static final int MILLIS_PER_SEC = Amount.of(1, Time.SECONDS).as(Time.MILLISECONDS);

  private final Supplier<? extends Number> valueAccessor;
  private final LinkedList<Pair<Long, Double>> samples = Lists.newLinkedList();
  private final Clock clock;
  private final long windowLengthMs;

  @VisibleForTesting
  <T extends Number> Rate(String name, Supplier<T> valueAccessor,
      Amount<Long, Time> windowLength, Clock clock) {
    super(name + "_per_" + windowLength.getValue() + windowLength.getUnit().toString());
    this.valueAccessor = Preconditions.checkNotNull(valueAccessor);
    this.windowLengthMs = windowLength.as(Time.MILLISECONDS);
    this.clock = Preconditions.checkNotNull(clock);
  }

  /**
   * Creates a rate using a supplier to access values.
   *
   * @param name Name of the rate.
   * @param valueAccessor Supplier to access values.
   * @param windowLength Sliding window duration for computing rate.
   * @param <T> Supplier type.
   */
  public <T extends Number> Rate(String name, Supplier<T> valueAccessor,
      Amount<Long, Time> windowLength) {
    this(name, valueAccessor, windowLength, Clock.SYSTEM_CLOCK);
  }

  /**
   * Creates a rate of a number.
   *
   * @param name Name of the rate.
   * @param number Rate input.
   * @return A rate that computes rate(number).
   */
  public static Rate of(String name, Number number) {
    return of(name, number, Clock.SYSTEM_CLOCK);
  }

  @VisibleForTesting
  static Rate of(String name, Number number, Clock clock) {
    return new Rate(name, Suppliers.ofInstance(number), ONE_SECOND, clock);
  }

  /**
   * Creates a rate of a gauge.
   *
   * @param gauge Rate input.
   * @return A rate that computes rate(gauge).
   */
  public static Rate of(Gauge gauge) {
    return of(gauge, Clock.SYSTEM_CLOCK);
  }

  @VisibleForTesting
  static Rate of(Gauge gauge, Clock clock) {
    return new Rate(gauge.getName(), Gauges.asSupplier(gauge), ONE_SECOND, clock);
  }

  @Override
  public Double read() {
    long now = clock.nowMillis();
    long retainThreshold = now - windowLengthMs;
    while (!samples.isEmpty() && samples.peekLast().getFirst() < retainThreshold) {
      samples.removeLast();
    }

    double newSample = valueAccessor.get().doubleValue();
    samples.addFirst(Pair.of(now, newSample));

    if (samples.size() == 1) {
      return 0d;
    }

    Pair<Long, Double> oldestSample = samples.getLast();
    double dy = newSample - oldestSample.getSecond();
    double dt = now - oldestSample.getFirst();
    return dt == 0 ? 0 : ((dy * MILLIS_PER_SEC) / dt);
  }
}
