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

package com.twitter.common.args;

import java.lang.reflect.Type;

/**
 * An interface to a command line argument parser.
 *
 * @param <T> The base class this parser can parse all subtypes of.
 */
public interface Parser<T> {
  /**
   * Parses strings as arguments of a given subtype of {@code T}.
   *
   * @param parserOracle The registered parserOracle for delegation.
   * @param type The target type of the parsed argument.
   * @param raw The raw value of the argument.
   * @return A value of the given type parsed from the raw value.
   * @throws IllegalArgumentException if the raw value could not be parsed into a value of the
   *     given type.
   */
  T parse(ParserOracle parserOracle, Type type, String raw) throws IllegalArgumentException;
}
