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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Ticker;

import com.twitter.common.collections.Pair;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Function to compute a windowed per-second rate of a value.
 *
 * @author William Farner
 */
public class Rate<T extends Number> extends SampledStat<Double> {

  private static final int DEFAULT_WINDOW_SIZE = 1;
  private static final double DEFAULT_SCALE_FACTOR = 1;
  private static final long NANOS_PER_SEC = Amount.of(1L, Time.SECONDS).as(Time.NANOSECONDS);

  private final Supplier<T> inputAccessor;
  private final Ticker ticker;
  private final double scaleFactor;

  private final LinkedBlockingDeque<Pair<Long, Double>> samples;

  private Rate(String name, Supplier<T> inputAccessor, int windowSize, double scaleFactor,
      Ticker ticker) {
    super(name, 0d);

    this.inputAccessor = Preconditions.checkNotNull(inputAccessor);
    this.ticker = Preconditions.checkNotNull(ticker);
    samples = new LinkedBlockingDeque<Pair<Long, Double>>(windowSize);
    Preconditions.checkArgument(scaleFactor != 0, "Scale factor must be non-zero!");
    this.scaleFactor = scaleFactor;
  }

  public static <T extends Number> Builder<T> of(Stat<T> input) {
    return new Builder<T>(input);
  }

  public static Builder<Long> of(String name, Supplier<Long> input) {
    return new Builder<Long>(name, input);
  }

  public static Builder<AtomicInteger> of(String name, AtomicInteger input) {
    return new Builder<AtomicInteger>(name, input);
  }

  public static Builder<AtomicLong> of(String name, AtomicLong input) {
    return new Builder<AtomicLong>(name, input);
  }

  @Override
  public Double doSample() {
    T newSample = inputAccessor.get();
    long newTimestamp = ticker.read();

    double rate = 0;
    if (!samples.isEmpty()) {
      Pair<Long, Double> oldestSample = samples.peekLast();

      double dy = newSample.doubleValue() - oldestSample.getSecond();
      double dt = newTimestamp - oldestSample.getFirst();
      rate = dt == 0 ? 0 : (NANOS_PER_SEC * scaleFactor * dy) / dt;
    }

    if (samples.remainingCapacity() == 0) samples.removeLast();
    samples.addFirst(Pair.of(newTimestamp, newSample.doubleValue()));

    return rate;
  }

  public static class Builder<T extends Number> {

    private String name;
    private int windowSize = DEFAULT_WINDOW_SIZE;
    private double scaleFactor = DEFAULT_SCALE_FACTOR;
    private Supplier<T> inputAccessor;
    private Ticker ticker = Ticker.systemTicker();

    Builder(String name, final T input) {
      this.name = name;
      inputAccessor = Suppliers.ofInstance(input);
    }

    Builder(String name, Supplier<T> input) {
      this.name = name;
      inputAccessor = input;
    }

    Builder(final Stat<T> input) {
      Stats.export(input);
      this.name = input.getName() + "_per_sec";
      inputAccessor = new Supplier<T>() {
        @Override public T get() { return input.read(); }
      };
    }

    public Builder<T> withName(String name) {
      this.name = name;
      return this;
    }

    public Builder<T> withWindowSize(int windowSize) {
      this.windowSize = windowSize;
      return this;
    }

    public Builder<T> withScaleFactor(double scaleFactor) {
      this.scaleFactor = scaleFactor;
      return this;
    }

    @VisibleForTesting
    Builder<T> withTicker(Ticker ticker ) {
      this.ticker = ticker;
      return this;
    }

    public Rate<T> build() {
      return new Rate<T>(name, inputAccessor, windowSize, scaleFactor, ticker);
    }
  }
}
