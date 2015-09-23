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
 * Interface representing statistics of a set of (long) elements.
 */
public interface StatisticsInterface {
  /**
   * Add a value in the Statistics object.
   * @param value value that you want to accumulate.
   */
  void accumulate(long value);

  /**
   * Clear the Statistics instance (equivalent to recreate a new one).
   */
  void clear();

  /**
   * Return the variance of the inserted elements.
   */
  double variance();

  /**
   * Return the standard deviation of the inserted elements.
   */
  double standardDeviation();

  /**
   * Return the mean of the inserted elements.
   */
  double mean();

  /**
   * Return the min of the inserted elements.
   */
  long min();

  /**
   * Return the max of the inserted elements.
   */
  long max();

  /**
   * Return the range of the inserted elements.
   */
  long range();

  /**
   * Return the sum of the inserted elements.
   */
  long sum();

  /**
   * Return the number of the inserted elements.
   */
  long populationSize();
}
