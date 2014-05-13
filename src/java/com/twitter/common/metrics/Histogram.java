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

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.twitter.common.base.MorePreconditions;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.Precision;
import com.twitter.common.stats.WindowedApproxHistogram;
import com.twitter.common.stats.WindowedStatistics;
import com.twitter.common.util.Clock;

import static com.twitter.common.stats.WindowedApproxHistogram.DEFAULT_MAX_MEMORY;
import static com.twitter.common.stats.WindowedApproxHistogram.DEFAULT_SLICES;
import static com.twitter.common.stats.WindowedApproxHistogram.DEFAULT_WINDOW;

/**
 * A Histogram is a representation of a distribution of values.
 * It can be queried for quantiles or basic statistics (min, max, avg, count).
 */
public class Histogram implements HistogramInterface {
  @VisibleForTesting
  public static final double[] DEFAULT_QUANTILES = {.50, .90, .95, .99, .999, .9999};

  private final String name;
  private final com.twitter.common.stats.Histogram histogram;
  private final WindowedStatistics stats;
  private final double[] quantiles;

  /**
   * Construct a Histogram.
   * This constructor only exists for backward compatibility reasons.
   * See #Histogram(String, Amount<Long, Time>, int, Amount<Long, Data>,
   *   Precision, double[], Clock)
   */
  public Histogram(String name, Amount<Long, Time> window, int slices,
      @Nullable Amount<Long, Data> maxMemory, @Nullable Precision precision,
      double[] quantiles,
      Clock clock,
      @Nullable MetricRegistry registry) {
    Preconditions.checkArgument(precision != null ^ maxMemory != null,
        "You must specify either memory or precision constraint but not both!");
    Preconditions.checkNotNull(window);
    Preconditions.checkArgument(0 < slices);
    for (double q: quantiles) {
      Preconditions.checkArgument(0.0 <= q && q <= 1.0);
    }
    Preconditions.checkNotNull(clock);

    this.name = MorePreconditions.checkNotBlank(name);
    this.quantiles = Preconditions.checkNotNull(quantiles);
    if (maxMemory != null) {
      this.histogram = new WindowedApproxHistogram(window, slices, maxMemory, clock);
    } else {
      this.histogram = new WindowedApproxHistogram(window, slices, precision, clock);
    }
    this.stats = new WindowedStatistics(window, slices, clock);

    if (registry != null) {
      registry.registerHistogram(this);
    }
  }

  /**
   * Default constructor
   * This histogram is composed of a WindowedHistogram with a window duration of {@code window}
   * and decomposed in {@code slices} Histograms (See #WindowedHistogram for more details about
   * that).
   *
   * @param window duration of the window
   * @param slices number of slices in the window
   * @param maxMemory maximum memory used by the whole histogram (can be null if precision isn't)
   * @param precision precision of the whole histogram (can be null if maxMemory isn't)
   * @param quantiles array of quantiles that will be computed
   * @param clock clock used to store elements in the the WindowedHistogram (for testing purposes
   * only)
   */
  public Histogram(String name, Amount<Long, Time> window, int slices,
      @Nullable Amount<Long, Data> maxMemory, @Nullable Precision precision,
      double[] quantiles,
      Clock clock) {
    this(name, window, slices,
        maxMemory, precision,
        quantiles,
        clock,
        null);
  }

  /**
   * Construct a Histogram with default arguments except name.
   * @see #Histogram(String, Amount, int, Amount, Precision, double[], Clock, MetricRegistry)
   */
  public Histogram(String name) {
    this(name, DEFAULT_WINDOW, DEFAULT_SLICES,
        DEFAULT_MAX_MEMORY, null,
        DEFAULT_QUANTILES,
        Clock.SYSTEM_CLOCK,
        null);
  }

  /**
   * Construct a Histogram with default arguments except name.
   * @see #Histogram(String, Amount, int, Amount, Precision, double[], Clock, MetricRegistry)
   *
   * 12/11/2013: Remove this method after the next deprecation cycle.
   * @deprecated Prefer registry.createHistogram(String)
   */
  @Deprecated
  public Histogram(String name, MetricRegistry registry) {
    this(name, DEFAULT_WINDOW, DEFAULT_SLICES,
        DEFAULT_MAX_MEMORY, null,
        DEFAULT_QUANTILES,
        Clock.SYSTEM_CLOCK,
        registry);
  }

  /**
   * Construct a Histogram with default arguments except name and precision.
   * @see #Histogram(String, Amount, int, Amount, Precision, double[], Clock, MetricRegistry)
   */
  public Histogram(String name, Precision precision) {
    this(name, DEFAULT_WINDOW, DEFAULT_SLICES,
        null, precision,
        DEFAULT_QUANTILES,
        Clock.SYSTEM_CLOCK,
        null);
  }

  /**
   * Construct a Histogram with default arguments except name and maxMemory.
   * @see #Histogram(String, Amount, int, Amount, Precision, double[], Clock, MetricRegistry)
   */
  public Histogram(String name, Amount<Long, Data> maxMemory) {
    this(name, DEFAULT_WINDOW, DEFAULT_SLICES,
        maxMemory, null,
        DEFAULT_QUANTILES,
        Clock.SYSTEM_CLOCK,
        null);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public synchronized void clear() {
    stats.clear();
    histogram.clear();
  }

  @Override
  public synchronized void add(long n) {
    stats.accumulate(n);
    histogram.add(n);
  }

  @Override
  public synchronized Snapshot snapshot() {
    stats.refresh();
    final long count = stats.populationSize();
    final long sum = stats.sum();
    final double avg = stats.mean();
    final long min = count == 0 ? 0L : stats.min();
    final long max = count == 0 ? 0L : stats.max();
    final double stddev = stats.standardDeviation();
    final Percentile[] ps = new Percentile[quantiles.length];
    long[] values = histogram.getQuantiles(quantiles);
    for (int i = 0; i < ps.length; i++) {
      ps[i] = new Percentile(quantiles[i], values[i]);
    }

    return new Snapshot() {
      @Override public long count() { return count; }
      @Override public long sum() { return sum; }
      @Override public double avg() { return avg; }
      @Override public long min() { return min; }
      @Override public long max() { return max; }
      @Override public double stddev() { return stddev; }
      @Override public Percentile[] percentiles() { return ps; }
    };
  }
}
