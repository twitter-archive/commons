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

import com.google.common.reflect.TypeToken;

/**
 * A registry of Parsers for different supported argument types.
 */
public interface ParserOracle {

  /**
   * Gets the parser associated with a class.
   *
   * @param type Type to get the parser for.
   * @return The parser for {@code cls}.
   * @throws IllegalArgumentException If no parser was found for {@code cls}.
   */
  <T> Parser<T> get(TypeToken<T> type) throws IllegalArgumentException;
}
