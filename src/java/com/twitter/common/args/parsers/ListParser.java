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

import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import com.twitter.common.args.Parsers;
import com.twitter.common.args.Parsers.Parser;

import static com.twitter.common.args.Parsers.checkedGet;

/**
 * List parser.
 *
 * @author William Farner
 */
public class ListParser extends TypeParameterizedParser<List> {

  public ListParser() {
    super(List.class, 1);
  }

  @Override
  List doParse(String raw, List<Class<?>> paramParsers) {
    final Parser parser = checkedGet(paramParsers.get(0));

    return ImmutableList.copyOf(Iterables.transform(Parsers.MULTI_VALUE_SPLITTER.split(raw),
        new Function<String, Object>() {
          @Override public Object apply(String raw) {
            return parser.parse(null, raw);
          }
        }));
  }
}
