package com.twitter.common.stats;

import com.google.common.base.Supplier;
import com.google.common.base.Function;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

/**
 * Keep track of statistics over a set of value in a sliding window.
 * @see Windowed class for more details about how the window is parametrized.
 * As Statistics cannot be summed easily, this class keep n Statistics instances responsible
 * for multiple window over time.
 *
 * <pre>
 * AAAAAAAAAAAAAAA
 *      BBBBBBBBBBBBBBB
 *           CCCCCCCCCCCCCCC
 *                DDDDDDDDDDDDDDD
 *                     AAAAAAAAAAAAAAA
 *                |    |
 * --------------------------------> t
 *                t=0  t=1
 *
 * At t=0, we insert values in {B,C,D} and query {A}
 * At t=1, we insert values in {C,D, A} and query {B} (note {A} is reused here)
 * and so on...
 * </pre>
 */
public class WindowedStatistics extends Windowed<Statistics> implements StatisticsInterface {
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

  public void accumulate(long value) {
    sync(getCurrentIndex());
    for (int i=0; i<buffers.length - 1; i++) {
      int j = (buffers.length + getCurrentIndex() - i) % buffers.length;
      buffers[j].accumulate(value);
    }
  }

  private Statistics getPrevious() {
    sync(getCurrentIndex());
    int i = (getCurrentIndex() + 1) % buffers.length;
    return buffers[i];
  }

  public double variance() {
    return getPrevious().variance();
  }

  public double standardDeviation() {
    return getPrevious().standardDeviation();
  }

  public double mean() {
    return getPrevious().mean();
  }

  public long sum() {
    return getPrevious().sum();
  }

  public long min() {
    return getPrevious().min();
  }

  public long max() {
    return getPrevious().max();
  }

  public long range() {
    return getPrevious().range();
  }

  public long populationSize() {
    return getPrevious().populationSize();
  }
}
