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

import java.util.concurrent.LinkedBlockingDeque;

import com.google.common.base.Preconditions;

/**
 * Function to compute the moving average of a time series.
 *
 * @author William Farner
 */
public class MovingAverage<T extends Number> extends SampledStat<Double> {

  private static final int DEFAULT_WINDOW = 10;
  private final Stat<T> input;

  private final LinkedBlockingDeque<T> samples;
  private double sampleSum = 0;

  private MovingAverage(String name, Stat<T> input, int windowSize) {
    super(name, 0d);
    Preconditions.checkArgument(windowSize > 1);

    this.input = Preconditions.checkNotNull(input);
    this.samples = new LinkedBlockingDeque<T>(windowSize);
    Stats.export(input);
  }

  public static <T extends Number> MovingAverage<T> of(Stat<T> input) {
    return MovingAverage.of(input, DEFAULT_WINDOW);
  }

  public static <T extends Number> MovingAverage<T> of(Stat<T> input, int windowSize) {
    return MovingAverage.of(String.format("%s_avg", input.getName()), input, windowSize);
  }

  public static <T extends Number> MovingAverage<T> of(String name, Stat<T> input,
      int windowSize) {
    return new MovingAverage<T>(name, input, windowSize);
  }

  @Override
  public Double doSample() {
    T sample = input.read();

    if (samples.remainingCapacity() == 0) {
      sampleSum -= samples.removeLast().doubleValue();
    }

    samples.addFirst(sample);
    sampleSum += sample.doubleValue();

    return sampleSum / samples.size();
  }
}
