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

import java.lang.reflect.Type;
import java.util.List;

import com.twitter.common.args.Parser;
import com.twitter.common.args.ParserOracle;
import com.twitter.common.args.TypeUtil;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Parser that makes implementation of parsers for parameterized types simpler.
 *
 * @param <T> The raw type this parser can parse.
 *
 * @author William Farner
 */
public abstract class TypeParameterizedParser<T> implements Parser<T> {

  private final int typeParamCount;

  /**
   * Creates a new type parameterized parser.
   *
   * @param typeParamCount Strict number of type parameters to allow on the assigned type.
   */
  TypeParameterizedParser(int typeParamCount) {
    this.typeParamCount = typeParamCount;
  }

  /**
   * Performs the actual parsing.
   *
   * @param parserOracle The registered parserOracle for delegation.
   * @param raw The raw value to parse.
   * @param typeParams The captured actual type parameters for {@code T}.
   * @return The parsed value.
   * @throws IllegalArgumentException If the value could not be parsed.
   */
  abstract T doParse(ParserOracle parserOracle, String raw, List<Type> typeParams)
      throws IllegalArgumentException;

  @Override public T parse(ParserOracle parserOracle, Type type, String raw) {
    List<Type> typeParams = TypeUtil.getTypeParams(type);
    checkArgument(typeParams.size() == typeParamCount, String.format(
        "Expected %d type parameters for %s but got %d",
        typeParamCount, type, typeParams.size()));

    return doParse(parserOracle, raw, typeParams);
  }
}
