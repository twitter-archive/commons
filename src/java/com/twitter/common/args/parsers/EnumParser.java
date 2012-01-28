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

import com.twitter.common.args.ArgParser;
import com.twitter.common.args.Parser;
import com.twitter.common.args.ParserOracle;

/**
 * An {@link Enum} parser that matches enum values via {@link Enum#valueOf(Class, String)}.
 *
 * @author John Sirois
 */
@ArgParser
public class EnumParser<T extends Enum<T>> implements Parser<T> {

  @Override
  public T parse(ParserOracle parserOracle, Type type, String raw) throws IllegalArgumentException {
    @SuppressWarnings("unchecked")
    Class<T> enumClass = (Class<T>) type;
    return Enum.valueOf(enumClass, raw);
  }
}
