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

import com.google.common.base.Preconditions;

import com.twitter.common.args.ArgParser;
import com.twitter.common.args.ParserOracle;
import com.twitter.common.args.TypeUtil;

/**
 * Class parser.
 *
 * @author William Farner
 */
@ArgParser
public class ClassParser extends TypeParameterizedParser<Class<?>> {

  public ClassParser() {
    super(1);
  }

  @Override
  public Class<?> doParse(ParserOracle parserOracle, String raw, final List<Type> typeParams) {
    Class<?> rawClassType = TypeUtil.getRawType(typeParams.get(0));
    try {
      Class<?> actualClass = Class.forName(raw);
      Preconditions.checkArgument(rawClassType.isAssignableFrom(actualClass));
      return actualClass;
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Could not find class " + raw);
    }
  }
}
