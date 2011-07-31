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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.util.Sampler;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A stats tracker to export percentiles of inputs based on a sampling rate.
 *
 * @author William Farner
 */
public class Percentile<T extends Number> {

  @VisibleForTesting
  static final int MAX_BUFFER_SIZE = 10000;

  private final Sampler sampler;

  private final Map<Double, SampledStat<Double>> percentiles;
  @VisibleForTesting
  final LinkedList<T> samples = Lists.newLinkedList();

  /**
   * Creates a new percentile tracker.
   *
   * A percentile tracker will randomly sample recorded events with the given sampling rate, and
   * will automatically register variables to track the percentiles requested.
   * Percentiles are windowed, in that once the last percentile is sampled, all recorded values are
   * flushed in preparation for the next window.
   *
   * @param name The name of the value whose percentile is being tracked.
   * @param samplePercent The percent of events to sample [0, 100].
   * @param percentiles The percentiles to track.
   */
  public Percentile(String name, float samplePercent, double... percentiles) {
    this(name, new Sampler(samplePercent), percentiles);
  }

  /**
   * Creates a new percentile tracker.
   *
   * A percentile tracker will randomly sample recorded events with the given sampling rate, and
   * will automatically register variables to track the percentiles requested.
   * Percentiles are windowed, in that once the last percentile is sampled, all recorded values are
   * flushed in preparation for the next window.
   *
   * @param name The name of the value whose percentile is being tracked.
   * @param sampler The sampler to use for selecting recorded events.
   * @param percentiles The percentiles to track.
   */
  public Percentile(String name, Sampler sampler, double... percentiles) {
    this(name, true, sampler, percentiles);
  }

  /**
   * Creates a new percentile tracker.
   *
   * A percentile tracker will randomly sample recorded events with the given sampling rate, and
   * will automatically register variables to track the percentiles requested.
   * When allowFlushAfterSample is set to true, once the last percentile is sampled,
   * all recorded values are flushed in preparation for the next window; otherwise, the percentile
   * is calculated using the moving window of the most recent values.
   *
   * @param name The name of the value whose percentile is being tracked.
   * @param allowFlushAfterSample Whether tp allow auto-flush.
   * @param sampler The sampler to use for selecting recorded events. You may set sampler to null
   *        to sample all input.
   * @param percentiles The percentiles to track.
   */
  public Percentile(String name, boolean allowFlushAfterSample,
                    @Nullable Sampler sampler, double... percentiles) {
    MorePreconditions.checkNotBlank(name);
    Preconditions.checkNotNull(percentiles);
    Preconditions.checkArgument(percentiles.length > 0, "Must specify at least one percentile.");

    this.sampler = sampler;

    ImmutableMap.Builder<Double, SampledStat<Double>> builder =
        new ImmutableMap.Builder<Double, SampledStat<Double>>();

    for (int i = 0; i < percentiles.length; i++) {
      boolean sortFirst = i == 0;
      boolean flushAfterSampling = allowFlushAfterSample && (i == percentiles.length - 1);
      String statName = String.format("%s_%s_percentile", name, percentiles[i])
          .replace('.', '_');

      SampledStat<Double> stat =
          new PercentileVar(statName, percentiles[i], sortFirst, flushAfterSampling);
      Stats.export(stat);
      builder.put(percentiles[i], stat);
    }

    this.percentiles = builder.build();
  }



  /**
   * Get the variables associated with this percentile tracker.
   *
   * @return A map from tracked percentile to the Stat corresponding to it
   */
  public Map<Double, ? extends Stat> getPercentiles() {
    return ImmutableMap.copyOf(percentiles);
  }

  @VisibleForTesting
  SampledStat<Double> getPercentile(double percentile) {
    return percentiles.get(percentile);
  }

  /**
   * Records an event.
   *
   * @param value The value to record if it is randomly selected based on the sampling rate.
   */
  public void record(T value) {
    if (sampler == null || sampler.select()) {
      synchronized (samples) {
        samples.addLast(value);

        while (samples.size() > MAX_BUFFER_SIZE) samples.removeFirst();
      }
    }
  }

  private class PercentileVar extends SampledStat<Double> {
    private final double percentile;
    private final boolean sortFirst;
    private final boolean flushAfterSampling;

    PercentileVar(String name, double percentile, boolean sortFirst, boolean flushAfterSampling) {
      super(name, 0d);
      this.percentile = percentile;
      this.sortFirst = sortFirst;
      this.flushAfterSampling = flushAfterSampling;
    }

    @Override
    public Double doSample() {
      synchronized (samples) {
        if (samples.isEmpty()) return 0d;
        if (sortFirst) Collections.sort(samples, NUMBER_COMPARATOR);

        int selectIndex = (int) ((samples.size() * percentile) / 100) - 1;
        selectIndex = selectIndex < 0 ? 0 : selectIndex;

        // Whether we need to interpolate between two values to calculate the percentile.
        boolean interpolate = ((selectIndex + 1) * 100) / percentile < samples.size();

        double value = samples.get(selectIndex).doubleValue();
        if (interpolate) value = (value + samples.get(selectIndex + 1).doubleValue()) / 2;

        if (flushAfterSampling) samples.clear();
        return value;
      }
    }
  }

  private static final Comparator<Number> NUMBER_COMPARATOR = new Comparator<Number>() {
    @Override
    public int compare(Number a, Number b) {
      return (int) Math.signum(a.doubleValue() - b.doubleValue());
    }
  };
}
