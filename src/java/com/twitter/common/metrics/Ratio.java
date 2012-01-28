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

package com.twitter.common.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Function to compute the ratio of two metrics.
 */
public class Ratio extends AbstractGauge<Double> {

  private final Supplier<? extends Number> numeratorAccessor;
  private final Supplier<? extends Number> denominatorAccessor;

  @VisibleForTesting
  <N extends Number, D extends Number> Ratio(String name,
      Supplier<N> numeratorAccessor, Supplier<D> denominatorAccessor) {
    super(name);
    this.numeratorAccessor = Preconditions.checkNotNull(numeratorAccessor);
    this.denominatorAccessor = Preconditions.checkNotNull(denominatorAccessor);
  }

  /**
   * Creates a ratio of two gauges, registering the result with a custom metric name.
   *
   * @param name Name to associate with the ratio.
   * @param numerator Numerator gauge.
   * @param denominator Denominator gauge.
   * @param <N> Numerator gauge type.
   * @param <D> Denominator gauge type.
   * @return A ratio that computes numerator / denominator.
   */
  public static <N extends Number, D extends Number> Ratio of(String name,
      final Gauge<N> numerator, final Gauge<D> denominator) {
    Preconditions.checkNotNull(numerator);
    Preconditions.checkNotNull(denominator);

    return new Ratio(name, Gauges.asSupplier(numerator), Gauges.asSupplier(denominator));
  }

  /**
   * Identical to {@link #of(String, Gauge, Gauge)}, but using a fixed naming format.
   *
   * @param numerator Numerator guage.
   * @param denominator Denominator gauge.
   * @param <N> Numerator gauge type.
   * @param <D> Denominator gauge type.
   * @return A ratio that computes numerator / denominator.
   */
  public static <N extends Number, D extends Number> Ratio of(final Gauge<N> numerator,
      final Gauge<D> denominator) {
    return of(numerator.getName() + "_per_" + denominator.getName(), numerator, denominator);
  }

  /**
   * Creates a ratio of two numbers.
   *
   * @param name Name to associate with the ratio.
   * @param numerator Numerator number.
   * @param denominator Denominator numbers.
   * @return A ratio that computes numerator / denominator.
   */
  public static Ratio of(String name, final Number numerator, final Number denominator) {
    Preconditions.checkNotNull(numerator);
    Preconditions.checkNotNull(denominator);

    return new Ratio(name, Suppliers.ofInstance(numerator), Suppliers.ofInstance(denominator));
  }

  @Override
  public Double read() {
    double numeratorValue = numeratorAccessor.get().doubleValue();
    double denominatorValue = denominatorAccessor.get().doubleValue();

    if ((denominatorValue == 0.0d)
        || (denominatorValue == Double.NaN)
        || (numeratorValue == Double.NaN)) {
      return Double.NaN;
    }

    return numeratorValue / denominatorValue;
  }
}
