package com.twitter.common.stats;

import com.google.common.base.Supplier;
import com.google.common.base.Function;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

/**
 * Keep track of statistics over a set of value in a sliding window.
 * WARNING: The computation of the statistics needs to be explicitly requested with
 * {@code refresh()} before reading any statistics.
 *
 * @see Windowed class for more details about how the window is parametrized.
 */
public class WindowedStatistics extends Windowed<Statistics> implements StatisticsInterface {
  private int lastIndex = -1;
  private double variance = 0.0;
  private double mean = 0.0;
  private long sum = 0L;
  private long min = Long.MAX_VALUE;
  private long max = Long.MIN_VALUE;
  private long populationSize = 0L;

  public WindowedStatistics(Amount<Long, Time> window, int slices, Clock clock) {
    super(Statistics.class, window, slices,
        new Supplier<Statistics>() {
          @Override public Statistics get() { return new Statistics(); }
        },
        new Function<Statistics, Statistics>() {
          @Override public Statistics apply(Statistics s) { s.clear(); return s; }
        },
        clock);
  }

  /**
   * Construct a Statistics sliced over time in {@code slices + 1} windows.
   * The {@code window} parameter represents the total window, that will be sliced into
   * {@code slices + 1} parts.
   *
   * Ex: WindowedStatistics(Amount.of(1L, Time.MINUTES), 3) will be sliced like this:
   * <pre>
   *        20s         20s         20s         20s
   *   [----A-----][-----B----][-----C----][-----D----]
   * </pre>
   * The current window is 'D' (the one you insert elements into) and the tenured windows
   * are 'A', 'B', 'C' (the ones you read elements from).
   */
  public WindowedStatistics(Amount<Long, Time> window, int slices) {
    this(window, slices, Clock.SYSTEM_CLOCK);
  }

  /**
   * Equivalent to calling {@link #WindowedStatistics(Amount, int)} with a 1 minute window
   * and 3 slices.
   */
  public WindowedStatistics() {
    this(Amount.of(1L, Time.MINUTES), 3, Clock.SYSTEM_CLOCK);
  }

  public void accumulate(long value) {
    getCurrent().accumulate(value);
  }

  /**
   * Compute all the statistics in one pass.
   */
  public void refresh() {
    int currentIndex = getCurrentIndex();
    if (lastIndex != currentIndex) {
      lastIndex = currentIndex;
      double x = 0.0;
      variance = 0.0;
      mean = 0.0;
      sum = 0L;
      populationSize = 0L;
      min = Long.MAX_VALUE;
      max = Long.MIN_VALUE;
      for (Statistics s : getTenured()) {
        if (s.populationSize() == 0) {
          continue;
        }
        x += s.populationSize() * (s.variance() + s.mean() * s.mean());
        sum += s.sum();
        populationSize += s.populationSize();
        min = Math.min(min, s.min());
        max = Math.max(max, s.max());
      }
      if (populationSize != 0) {
        mean = ((double) sum) / populationSize;
        variance = x / populationSize - mean * mean;
      }
    }
  }

  /**
   * WARNING: You need to call refresh() to recompute the variance
   * @return the variance of the aggregated windows
   */
  public double variance() {
    return variance;
  }

  /**
   * WARNING: You need to call refresh() to recompute the variance
   * @return the standard deviation of the aggregated windows
   */
  public double standardDeviation() {
    return Math.sqrt(variance());
  }

  /**
   * WARNING: You need to call refresh() to recompute the variance
   * @return the mean of the aggregated windows
   */
  public double mean() {
    return mean;
  }

  /**
   * WARNING: You need to call refresh() to recompute the variance
   * @return the sum of the aggregated windows
   */
  public long sum() {
    return sum;
  }

  /**
   * WARNING: You need to call refresh() to recompute the variance
   * @return the min of the aggregated windows
   */
  public long min() {
    return min;
  }

  /**
   * WARNING: You need to call refresh() to recompute the variance
   * @return the max of the aggregated windows
   */
  public long max() {
    return max;
  }

  /**
   * WARNING: You need to call refresh() to recompute the variance
   * @return the range of the aggregated windows
   */
  public long range() {
    return max - min;
  }

  /**
   * WARNING: You need to call refresh() to recompute the variance
   * @return the population size of the aggregated windows
   */
  public long populationSize() {
    return populationSize;
  }
}
