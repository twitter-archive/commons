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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.reflect.TypeToken;

import com.twitter.common.args.ArgParser;
import com.twitter.common.args.Parser;
import com.twitter.common.args.ParserOracle;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Unit;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Amount parser.
 *
 * @author William Farner
 */
@ArgParser
public class AmountParser extends TypeParameterizedParser<Amount<?, ?>> {

  private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+)([A-Za-z]+)");

  public AmountParser() {
    super(2);
  }

  @Override
  Amount<?, ?> doParse(ParserOracle parserOracle, String raw, List<Type> typeParams) {
    Type valueType = typeParams.get(0);
    Parser<?> parser = parserOracle.get(TypeToken.of(valueType));

    Matcher matcher = AMOUNT_PATTERN.matcher(raw);
    checkArgument(matcher.matches(),
        "Value '" + raw + "' must be of the format 1ns, 4mb, etc.");

    Number number = (Number) parser.parse(parserOracle, valueType, matcher.group(1));
    String unitRaw = matcher.group(2);

    Type unitType = typeParams.get(1);
    @SuppressWarnings("rawtypes")
    Parser<Unit> unitParser = parserOracle.get(TypeToken.of(Unit.class));
    @SuppressWarnings("rawtypes")
    Unit unit = unitParser.parse(parserOracle, unitType, unitRaw);
    checkArgument(unit.getClass() == unitType, String.format(
        "Unit type (%s) does not match argument type (%s).",
        unit.getClass(), unitType));

    return create(valueType, number, unit);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Amount<?, ?> create(Type valueType, Number number, Unit unit) {
    if (valueType == Integer.class) {
      return Amount.of(number.intValue(), unit);
    } else if (valueType == Double.class) {
      return Amount.of(number.doubleValue(), unit);
    } else if (valueType == Long.class) {
      return Amount.of(number.longValue(), unit);
    } else if (valueType == Byte.class) {
      return Amount.of(number.byteValue(), unit);
    } else if (valueType == Short.class) {
      return Amount.of(number.shortValue(), unit);
    } else if (valueType == Float.class) {
      return Amount.of(number.floatValue(), unit);
    }
    throw new IllegalArgumentException("Unrecognized number class " + valueType);
  }
}
