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

package com.twitter.common.util;

import com.google.common.base.Preconditions;

/**
 * A sampler that implements logic for fractional random selection.
 *
 * @author William Farner
 */
public class Sampler {

  private final Random rand;
  private final double threshold;

  /**
   * Creates a new sampler using the default system {@link Random}.
   *
   * @param selectPercent Percentage to randomly select, must be between 0 and 100 (inclusive).
   */
  public Sampler(float selectPercent) {
    this(selectPercent, Random.Util.newDefaultRandom());
  }

  /**
   * Creates a new sampler using the provided {@link Random}.
   *
   * @param selectPercent Percentage to randoml select, must be between 0 and 100 (inclusive).
   * @param rand The random utility to use for generating random numbers.
   */
  public Sampler(float selectPercent, Random rand) {
    Preconditions.checkArgument((selectPercent >= 0) && (selectPercent <= 100),
        "Invalid selectPercent value: " + selectPercent);

    this.threshold = selectPercent / 100;
    this.rand = Preconditions.checkNotNull(rand);
  }

  public boolean select() {
    return rand.nextDouble() < threshold;
  }
}
