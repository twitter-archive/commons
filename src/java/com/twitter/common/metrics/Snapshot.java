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

/**
 * A Snapshot represents an instantaneous capture of histogram statistics.
 * It is guarantee that the snapshot's statistics are always consistent.
 */
public interface Snapshot {

  /**
   * Returns the number of elements inserted in the Histogram.
   */
  long count();

  /**
   * Returns the sum of the elements inserted in the Histogram.
   */
  long sum();

  /**
   * Returns the average of all the elements inserted in the Histogram.
   */
  double avg();

  /**
   * Returns the min of all the elements inserted in the Histogram.
   */
  long min();

  /**
   * Returns the max of all the elements inserted in the Histogram.
   */
  long max();

  /**
   * Returns the standard deviation of all the elements inserted in the Histogram.
   */
  double stddev();

  /**
   * Returns an array of Percentiles based on the distribution of the elements inserted in the
   * Histogram.
   */
  Percentile[] percentiles();
}
