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

import com.google.common.base.Preconditions;

/**
 * This is a simple class representing a Percentile (or Quantile).
 * It only contains the quantile (as a double) and its value.
 */
public class Percentile {
  private final double quantile;
  private final long value;

  public Percentile(double quantile, long value) {
    Preconditions.checkArgument(0.0 <= quantile && quantile <= 1.0);
    this.quantile = quantile;
    this.value = value;
  }

  /**
   * Returns the percentile (or quantile) for this value.
   */
  public double getQuantile() {
    return quantile;
  }

  /**
   * Returns the value for this percentile (or quantile).
   */
  public long getValue() {
    return value;
  }
}
