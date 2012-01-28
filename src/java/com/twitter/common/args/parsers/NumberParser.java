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

package com.twitter.common.args.parsers;

/**
 * Parser that handles common functionality for parsing numbers.
 *
 * @author William Farner
 */
public abstract class NumberParser<T extends Number> extends NonParameterizedTypeParser<T> {

  /**
   * Performs the actual parsing of the value into the target type.
   *
   * @param raw Raw value to parse.
   * @return The parsed value.
   * @throws NumberFormatException If the raw value does not represent a valid number of the target
   *    type.
   */
  abstract T parseNumber(String raw) throws NumberFormatException;

  @Override
  public T doParse(String raw) throws IllegalArgumentException {
    try {
      return parseNumber(raw);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(String.format("Invalid value: " + e.getMessage()));
    }
  }
}
