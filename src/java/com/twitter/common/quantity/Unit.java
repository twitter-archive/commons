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

package com.twitter.common.quantity;

/**
 * Represents a unit hierarchy for a given unit of measure; eg: time.  Instances represent specific
 * units from the hierarchy; eg: seconds.
 *
 * @param <U> the type of the concrete unit implementation
 *
 * @author John Sirois
 */
public interface Unit<U extends Unit<U>> {

  /**
   * Returns the weight of this unit relative to other units in the same hierarchy.  Typically the
   * smallest unit in the hierarchy returns 1, but this need not be the case.  It is only required
   * that each unit of the hierarchy return a multiplier relative to a common base unit for the
   * hierarchy.
   */
  double multiplier();
}
