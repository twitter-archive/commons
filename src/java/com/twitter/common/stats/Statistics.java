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

/**
 * A simple class to keep running statistics that require O(1) storage.
 *
 * @author William Farner
 */
public class Statistics implements StatisticsInterface {
  private long populationSize;
  private long sum;
  private double accumulatedVariance;
  private double runningMean;

  private long minValue;
  private long maxValue;

  public Statistics() {
    clear();
  }

  public void accumulate(long value) {
    populationSize++;
    sum += value;
    double delta = value - runningMean;
    runningMean += delta / populationSize;
    accumulatedVariance += delta * (value - runningMean);

    // Update max/min.
    minValue = value < minValue ? value : minValue;
    maxValue = value > maxValue ? value : maxValue;
  }

  public void clear() {
    populationSize = 0;
    sum = 0;
    accumulatedVariance = 0;
    runningMean = 0;
    minValue = Long.MAX_VALUE;
    maxValue = Long.MIN_VALUE;
  }

  public double variance() {
    return accumulatedVariance / populationSize;
  }

  public double standardDeviation() {
    return Math.sqrt(variance());
  }

  public double mean() {
    return runningMean;
  }

  public long min() {
    return minValue;
  }

  public long max() {
    return maxValue;
  }

  public long range() {
    return maxValue - minValue;
  }

  public long sum() {
    return sum;
  }

  public long populationSize() {
    return populationSize;
  }

  @Override
  public String toString() {
    return String.format("Mean: %f, Min: %d, Max: %d, Range: %d, Stddev: %f, Variance: %f, " +
        "Population: %d, Sum: %d", mean(), min(), max(), range(), standardDeviation(),
        variance(), populationSize(), sum());
  }
}
