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

package com.twitter.common.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Supplier;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

/**
 * WindowedApproxHistogram is an implementation of WindowedHistogram with an
 * ApproximateHistogram as the underlying storing histogram.
 */
public class WindowedApproxHistogram extends WindowedHistogram<ApproximateHistogram> {
  @VisibleForTesting public static final int DEFAULT_SLICES = 3;
  @VisibleForTesting public static final Amount<Long, Time> DEFAULT_WINDOW =
      Amount.of(1L, Time.MINUTES);
  @VisibleForTesting public static final Amount<Long, Data> DEFAULT_MAX_MEMORY = Amount.of(
      (DEFAULT_SLICES + 1) * ApproximateHistogram.DEFAULT_MAX_MEMORY.as(Data.BYTES), Data.BYTES);

  /**
   * Create a {@code WindowedApproxHistogram } with a window duration of {@code window} and
   * decomposed in {@code slices} Histograms. Those Histograms will individually take less than
   * {@code maxMemory / (slices + 1)}. The clock will be used to find the correct index in the
   * ring buffer.
   *
   * @param window duration of the window
   * @param slices number of slices in the window
   * @param maxMemory maximum memory used by the whole histogram
   */
  public WindowedApproxHistogram(Amount<Long, Time> window, final int slices,
      final Amount<Long, Data> maxMemory, Clock clock) {
    super(ApproximateHistogram.class, window, slices,
        new Supplier<ApproximateHistogram>() {
          private Amount<Long, Data> perHistogramMemory = Amount.of(
              maxMemory.as(Data.BYTES) / (slices + 1), Data.BYTES);
          @Override
          public ApproximateHistogram get() {
            return new ApproximateHistogram(perHistogramMemory);
          }
        },
        new Function<ApproximateHistogram[], Histogram>() {
          @Override
          public Histogram apply(ApproximateHistogram[] histograms) {
            return ApproximateHistogram.merge(histograms);
          }
        }, clock);
  }

  /**
   * Create a {@code WindowedApproxHistogram } with a window duration of {@code window} and
   * decomposed in {@code slices} Histograms. Those Histograms will individually have a
   * precision of {@code precision / (slices + 1)}. The ticker will be used to measure elapsed
   * time in the WindowedHistogram.
   *
   * @param window duration of the window
   * @param slices number of slices in the window
   * @param precision precision of the whole histogram
   */
  public WindowedApproxHistogram(Amount<Long, Time> window, final int slices,
      final Precision precision, Clock clock) {
    super(ApproximateHistogram.class, window, slices,
        new Supplier<ApproximateHistogram>() {
          private Precision perHistogramPrecision = new Precision(
              precision.getEpsilon(), precision.getN() / (slices + 1));
          @Override
          public ApproximateHistogram get() {
            return new ApproximateHistogram(perHistogramPrecision);
          }
        },
        new Function<ApproximateHistogram[], Histogram>() {
          @Override
          public Histogram apply(ApproximateHistogram[] histograms) {
            return ApproximateHistogram.merge(histograms);
          }
        }, clock);
  }

  /**
   * Equivalent to calling
   * {@link #WindowedApproxHistogram(Amount, int, Amount, Clock)}
   * with the System clock.
   */
  public WindowedApproxHistogram(Amount<Long, Time> window, int slices,
      Amount<Long, Data> maxMemory) {
    this(window, slices, maxMemory, Clock.SYSTEM_CLOCK);
  }

  /**
   * Equivalent to calling
   * {@link #WindowedApproxHistogram(Amount, int, Amount)}
   * with default window and slices.
   */
  public WindowedApproxHistogram(Amount<Long, Data> maxMemory) {
    this(DEFAULT_WINDOW, DEFAULT_SLICES, maxMemory);
  }

  /**
   * Equivalent to calling
   * {@link #WindowedApproxHistogram(Amount, int, Precision, Clock)}
   * with the System clock.
   */
  public WindowedApproxHistogram(Amount<Long, Time> window, int slices, Precision precision) {
    this(window, slices, precision, Clock.SYSTEM_CLOCK);
  }

  /**
   * Equivalent to calling
   * {@link #WindowedApproxHistogram(Amount, int, Precision)}
   * with default window and slices.
   */
  public WindowedApproxHistogram(Precision precision) {
    this(DEFAULT_WINDOW, DEFAULT_SLICES, precision);
  }

  /**
   * Equivalent to calling
   * {@link #WindowedApproxHistogram(Amount, int, Amount, Clock)}
   * with the default maxMemory parameter and System clock.
   */
  public WindowedApproxHistogram(Amount<Long, Time> window, int slices) {
    this(window, slices, DEFAULT_MAX_MEMORY, Clock.SYSTEM_CLOCK);
  }

  /**
   * WindowedApproxHistogram constructor with default values.
   */
  public WindowedApproxHistogram() {
    this(DEFAULT_WINDOW, DEFAULT_SLICES, DEFAULT_MAX_MEMORY, Clock.SYSTEM_CLOCK);
  }

  /**
   * WindowedApproxHistogram constructor with custom Clock (for testing purposes only).
   */
  @VisibleForTesting public WindowedApproxHistogram(Clock clock) {
    this(DEFAULT_WINDOW, DEFAULT_SLICES, DEFAULT_MAX_MEMORY, clock);
  }
}
