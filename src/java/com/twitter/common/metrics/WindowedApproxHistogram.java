package com.twitter.common.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Ticker;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.ApproximateHistogram;
import com.twitter.common.stats.Histogram;
import com.twitter.common.stats.Precision;

public class WindowedApproxHistogram extends WindowedHistogram<ApproximateHistogram> {
  @VisibleForTesting static final int DEFAULT_SLICES = 3;
  @VisibleForTesting static final Amount<Long, Time> DEFAULT_WINDOW = Amount.of(1L, Time.MINUTES);
  @VisibleForTesting static final Amount<Long, Data> DEFAULT_MAX_MEMORY = Amount.of(
      (DEFAULT_SLICES + 1) * ApproximateHistogram.DEFAULT_MAX_MEMORY.as(Data.BYTES), Data.BYTES);

  @VisibleForTesting
  WindowedApproxHistogram(Amount<Long, Time> window, final int slices,
      final Amount<Long, Data> maxMemory, Ticker ticker) {
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
        }, ticker);
  }

  @VisibleForTesting
  WindowedApproxHistogram(Amount<Long, Time> window, final int slices,
      final Precision precision, Ticker ticker) {
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
        }, ticker);
  }

  /**
   * Create a {@code WindowedApproxHistogram } with a window duration of {@code window} and
   * decomposed in {@code slices} Histograms.
   * @param window duration of the window
   * @param slices number of slices in the window
   */
  public WindowedApproxHistogram(Amount<Long, Time> window, int slices,
      Amount<Long, Data> maxMemory) {
    this(window, slices, maxMemory, Ticker.systemTicker());
  }

  /**
   * Equivalent to calling
   * {@link #WindowedApproxHistogram(Amount<Long, Time>, int, Amount<Long, Data>)} with default
   * window and slices.
   */
  public WindowedApproxHistogram(Amount<Long, Data> maxMemory) {
    this(DEFAULT_WINDOW, DEFAULT_SLICES, maxMemory);
  }

  /**
   * Create a {@code WindowedApproxHistogram } with a window duration of {@code window} and
   * decomposed in {@code slices} Histograms.
   * @param window duration of the window
   * @param slices number of slices in the window
   */
  public WindowedApproxHistogram(Amount<Long, Time> window, int slices, Precision precision) {
    this(window, slices, precision, Ticker.systemTicker());
  }

  /**
   * Equivalent to calling
   * {@link #WindowedApproxHistogram(Amount<Long, Time>, int, Precision} with default window and
   * slices.
   */
  public WindowedApproxHistogram(Precision precision) {
    this(DEFAULT_WINDOW, DEFAULT_SLICES, precision);
  }

  /**
   * WindowedApproxHistogram  constructor with default value.
   */
  public WindowedApproxHistogram() {
    this(DEFAULT_WINDOW, DEFAULT_SLICES, DEFAULT_MAX_MEMORY, Ticker.systemTicker());
  }
}
