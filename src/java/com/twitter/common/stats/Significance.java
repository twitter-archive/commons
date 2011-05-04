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
 * Calculate significance scores between an observed amount and an expected amount.
 *
 * @author Gilad Mishne
 */
public class Significance {

  /**
   * @param observed The observed amount.
   * @param expected The expected amount.
   * @return [(observed - expected) ** 2 / expected] * sign(observed - expected)
   */
  public static double chiSqrScore(double observed, double expected) {
    double score = Math.pow((observed - expected), 2) / expected;
    if (observed < expected) {
      score *= -1;
    }
    return score;
  }

  /**
   * @param observed The observed amount.
   * @param expected The expected amount.
   * @return -2 * expected * log(observed / expected) * sign(observed - expected)
   */
  public static double logLikelihood(double observed, double expected) {
    if (observed == 0) {
      return -expected;
    }
    if (expected == 0) {
      return observed;
    }
    double score = -2 * observed * Math.log(observed / expected);
    if (observed < expected) {
      score *= -1;
    }
    return score;
  }

  private Significance() {
    // prevent instantiation
  }

}
