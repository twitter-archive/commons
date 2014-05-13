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
import com.google.common.collect.Ordering;

import com.twitter.common.base.MorePreconditions;
import com.twitter.common.util.Sampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import javax.annotation.Nullable;

/**
 * A stats tracker to export percentiles of inputs based on a sampling rate.
 *
 * A percentile tracker will randomly sample recorded events with the given sampling rate, and
 * will automatically register variables to track the percentiles requested.
 * Percentiles are calculated based on the K most recent sampling windows, where each sampling
 * window has the recorded events for a sampling period.
 *
 * @author William Farner
 */
public class Percentile<T extends Number & Comparable<T>> {

  @VisibleForTesting
  static final int MAX_BUFFER_SIZE = 10001;

  private final Sampler sampler;

  private final Map<Double, SampledStat<Double>> statsByPercentile;
  @VisibleForTesting
  final LinkedList<T> samples = Lists.newLinkedList();

  private final LinkedBlockingDeque<ArrayList<T>> sampleQueue;
  private final ArrayList<T> allSamples = new ArrayList<T>();

  /**
   * Creates a new percentile tracker.
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
   * @param name The name of the value whose percentile is being tracked.
   * @param sampler The sampler to use for selecting recorded events.
   * @param percentiles The percentiles to track.
   */
  public Percentile(String name, Sampler sampler, double... percentiles) {
    this(name, 1, sampler, percentiles);
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
   * @param numSampleWindows How many sampling windows are used for calculation.
   * @param sampler The sampler to use for selecting recorded events. You may set sampler to null
   *        to sample all input.
   * @param percentiles The percentiles to track.
   */
  public Percentile(String name, int numSampleWindows,
      @Nullable Sampler sampler, double... percentiles) {
    MorePreconditions.checkNotBlank(name);
    Preconditions.checkArgument(numSampleWindows >= 1, "Must have one or more sample windows.");
    Preconditions.checkNotNull(percentiles);
    Preconditions.checkArgument(percentiles.length > 0, "Must specify at least one percentile.");

    this.sampler = sampler;

    sampleQueue = new LinkedBlockingDeque<ArrayList<T>>(numSampleWindows);

    ImmutableMap.Builder<Double, SampledStat<Double>> builder =
        new ImmutableMap.Builder<Double, SampledStat<Double>>();

    for (int i = 0; i < percentiles.length; i++) {
      boolean sortFirst = i == 0;
      String statName = String.format("%s_%s_percentile", name, percentiles[i])
          .replace('.', '_');

      SampledStat<Double> stat = new PercentileVar(statName, percentiles[i], sortFirst);
      Stats.export(stat);
      builder.put(percentiles[i], stat);
    }

    statsByPercentile = builder.build();
  }

  /**
   * Get the variables associated with this percentile tracker.
   *
   * @return A map from tracked percentile to the Stat corresponding to it
   */
  public Map<Double, ? extends Stat<?>> getPercentiles() {
    return ImmutableMap.copyOf(statsByPercentile);
  }

  @VisibleForTesting
  SampledStat<Double> getPercentile(double percentile) {
    return statsByPercentile.get(percentile);
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

    PercentileVar(String name, double percentile, boolean sortFirst) {
      super(name, 0d);
      this.percentile = percentile;
      this.sortFirst = sortFirst;
    }

    @Override
    public Double doSample() {
      synchronized (samples) {
        if (sortFirst) {
          if (sampleQueue.remainingCapacity() == 0) {
            sampleQueue.removeFirst();
          }
          sampleQueue.addLast(new ArrayList<T>(samples));
          samples.clear();

          allSamples.clear();
          for (ArrayList<T> sample : sampleQueue) {
            allSamples.addAll(sample);
          }

          Collections.sort(allSamples, Ordering.<T>natural());
        }

        if (allSamples.isEmpty()) {
          return 0d;
        }

        int maxIndex = allSamples.size() - 1;
        double selectIndex = maxIndex * percentile / 100;
        selectIndex = selectIndex < 0d ? 0d : selectIndex;
        selectIndex = selectIndex > maxIndex ? maxIndex : selectIndex;

        int indexLeft = (int) selectIndex;
        if (indexLeft == maxIndex) {
          return allSamples.get(indexLeft).doubleValue();
        }

        double residue = selectIndex - indexLeft;
        return allSamples.get(indexLeft).doubleValue() * (1 - residue) +
            allSamples.get(indexLeft + 1).doubleValue() * residue;
      }
    }
  }
}
