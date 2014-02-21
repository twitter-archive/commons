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

import java.util.EnumSet;
import java.util.Map;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import com.twitter.common.args.ArgParser;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;
import com.twitter.common.quantity.Unit;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Unit parser.
 * Units are matched (case sensitively) against the result of {@link Unit#toString()}.
 */
@ArgParser
public class UnitParser extends NonParameterizedTypeParser<Unit<?>> {

  private final Map<String, Unit<?>> unitValues;

  public UnitParser() {
    unitValues = Maps.uniqueIndex(
        ImmutableList.<Unit<?>>builder().add(Time.values()).add(Data.values()).build(),
        Functions.toStringFunction());
  }

  @Override
  public Unit<?> doParse(String raw) {
    Unit<?> unit = unitValues.get(raw);

    checkArgument(unit != null, String.format(
        "No Units found matching %s, options: (Time): %s, (Data): %s",
        raw, EnumSet.allOf(Time.class), EnumSet.allOf(Data.class)));
    return unit;
  }
}
