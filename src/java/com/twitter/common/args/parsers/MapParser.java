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
import java.util.Map;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.twitter.common.args.Parsers;
import com.twitter.common.args.Parsers.Parser;

import static com.google.common.base.Preconditions.checkArgument;
import static com.twitter.common.args.Parsers.checkedGet;

/**
 * Map parser.
 *
 * @author William Farner
 */
public class MapParser extends TypeParameterizedParser<Map> {

  private static final Splitter KEY_VALUE_SPLITTER = Splitter.on("=")
      .trimResults().omitEmptyStrings();

  public MapParser() {
    super(Map.class, 2);
  }

  @SuppressWarnings("unchecked")
  @Override
  Map doParse(String raw, List<Class<?>> paramParsers) {
    final Parser keyParser = checkedGet(paramParsers.get(0));
    final Parser valueParser = checkedGet(paramParsers.get(1));

    ImmutableMap.Builder map = ImmutableMap.builder();
    for (String keyAndValue : Parsers.MULTI_VALUE_SPLITTER.split(raw)) {
      List<String> fields = ImmutableList.copyOf(KEY_VALUE_SPLITTER.split(keyAndValue));
      checkArgument(fields.size() == 2,
          "Failed to parse key/value pair " + keyAndValue);

      map.put(keyParser.parse(null, fields.get(0)), valueParser.parse(null, fields.get(1)));
    }

    return map.build();
  }
}
