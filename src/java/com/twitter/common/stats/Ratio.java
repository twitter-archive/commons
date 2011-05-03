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

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

/**
 * Function to compute the ratio of two time series.
 * The first argument is the numerator, and the second is the denominator.  Assumes that the
 * timestamps of the two arguments are suitably synchronized (i.e. the ith point in the numerator
 * time series corresponds with the ith point of the denominator time series).
 *
 * @author William Farner
 */
public class Ratio extends SampledStat<Double> {

  private final Supplier<Number> numeratorAccessor;
  private final Supplier<Number> denominatorAccessor;

  private Ratio(String name, Supplier<Number> numeratorAccessor,
      Supplier<Number> denominatorAccessor) {
    super(name, 0d);
    this.numeratorAccessor = Preconditions.checkNotNull(numeratorAccessor);
    this.denominatorAccessor = Preconditions.checkNotNull(denominatorAccessor);
  }

  public static <T extends Number> Ratio of(Stat<T> numerator, Stat<T> denominator) {
    Preconditions.checkNotNull(numerator);
    Preconditions.checkNotNull(denominator);

    String name = String.format("%s_per_%s", numerator.getName(), denominator.getName());
    return Ratio.of(name, numerator, denominator);
  }

  public static <T extends Number> Ratio of(String name, final Stat<T> numerator,
      final Stat<T> denominator) {
    Preconditions.checkNotNull(numerator);
    Preconditions.checkNotNull(denominator);

    Stats.export(numerator);
    Stats.export(denominator);

    return new Ratio(name,
        new Supplier<Number>() {
          @Override public Number get() {
            return numerator.read();
          }
        },
        new Supplier<Number>() {
          @Override public Number get() {
            return denominator.read();
          }
        });
  }

  public static Ratio of(String name, final Number numerator, final Number denominator) {
    Preconditions.checkNotNull(numerator);
    Preconditions.checkNotNull(denominator);

    return new Ratio(name,
        new Supplier<Number>() {
          @Override public Number get() {
            return numerator;
          }
        },
        new Supplier<Number>() {
          @Override public Number get() {
            return denominator;
          }
        });
  }

  @Override
  public Double doSample() {
    double numeratorValue = numeratorAccessor.get().doubleValue();
    double denominatorValue = denominatorAccessor.get().doubleValue();

    if ((denominatorValue == 0)
        || (denominatorValue == Double.NaN)
        || (numeratorValue == Double.NaN)) {
      return 0d;
    }

    return numeratorValue / denominatorValue;
  }
}
