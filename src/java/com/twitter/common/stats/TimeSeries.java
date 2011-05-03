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

import java.util.Calendar;

/**
 * A time series of values.
 *
 * @author William Farner
 */
public interface TimeSeries {

  /**
   * A name describing this time series.
   *
   * @return The name of this time series data.
   */
  public String getName();

  /**
   * A series of numbers representing regular samples of a variable.
   *
   * @return The time series of sample values.
   */
  public Iterable<Number> getSamples();
}
