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
 * A variable that contains information about a (possibly changing) value.
 *
 * @author William Farner
 */
interface RecordingStat<T extends Number> extends Stat<T> {

  /**
   * Called by the variable sampler when a sample is being taken.  Only calls to this method should
   * be used to store variable history.
   *
   * Note - if the sampling of this value depends on other variables, it is imperative that those
   * variables values are updated first (and available via {@link Stat#read()}.
   *
   * @return A new sample of the variable.
   */
  public T sample();
}
