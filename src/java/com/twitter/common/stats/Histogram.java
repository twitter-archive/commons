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

package com.twitter.common.stats;

/**
 * An interface for Histogram
 */
public interface Histogram {

  /**
   * Add an entry into the histogram.
   * @param x the value to insert.
   */
  void add(long x);

  /**
   * Clear the histogram.
   */
  void clear();

  /**
   * Return the current quantile of the histogram.
   * @param quantile value to compute.
   */
  long getQuantile(double quantile);

  /**
   * Return the quantiles of the histogram.
   * @param quantiles array of values to compute.
   */
  long[] getQuantiles(double[] quantiles);
}
