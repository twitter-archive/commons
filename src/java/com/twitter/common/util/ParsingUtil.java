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

import com.twitter.common.collections.Pair;

/**
 * Common methods for parsing configs.
 *
 * @author John Sirois
 */
public class ParsingUtil {
  /**
   * Parses a string as a range between one integer and another.  The integers must be separated by
   * a hypen character (space padding is acceptable).  Additionally, the first integer
   * (left-hand side) must be less than or equal to the second (right-hand side).
   *
   * @param rangeString The string to parse as an integer range.
   * @return A pair of the parsed integers.
   */
  public static Pair<Integer, Integer> parseRange(String rangeString) {
    if (rangeString == null) return null;

    String[] startEnd = rangeString.split("-");
    Preconditions.checkState(
        startEnd.length == 2, "Shard range format: start-end (e.g. 1-4)");
    int start;
    int end;
    try {
      start = Integer.parseInt(startEnd[0].trim());
      end = Integer.parseInt(startEnd[1].trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Failed to parse shard range.", e);
    }

    Preconditions.checkState(
        start <= end, "The left-hand side of a shard range must be <= the right-hand side.");
    return Pair.of(start, end);
  }
}
