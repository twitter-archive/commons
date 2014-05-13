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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

import com.twitter.common.args.ArgParser;
import com.twitter.common.args.Parser;
import com.twitter.common.args.ParserOracle;
import com.twitter.common.args.Parsers;

/**
 * List parser.
 *
 * @author William Farner
 */
@ArgParser
public class ListParser extends TypeParameterizedParser<List<?>> {

  public ListParser() {
    super(1);
  }

  @Override
  List<?> doParse(final ParserOracle parserOracle, String raw, final List<Type> typeParams) {
    final Type listType = typeParams.get(0);
    final Parser<?> parser = parserOracle.get(TypeToken.of(listType));
    return ImmutableList.copyOf(Iterables.transform(Parsers.MULTI_VALUE_SPLITTER.split(raw),
        new Function<String, Object>() {
          @Override public Object apply(String raw) {
            return parser.parse(parserOracle, listType, raw);
          }
        }));
  }
}
