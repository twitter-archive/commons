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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.twitter.common.args.Parsers.Parser;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Unit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.twitter.common.args.Parsers.checkedGet;

/**
 * amount parser.
 *
 * @author William Farner
 */
public class AmountParser extends TypeParameterizedParser<Amount> {

  public AmountParser() {
    super(Amount.class, 2);
  }

  @SuppressWarnings("unchecked")
  @Override
  Amount doParse(String raw, List<Class<?>> paramParsers) {
    final Parser parser = checkedGet(paramParsers.get(0));

    Matcher matcher = Pattern.compile("(\\d+)([A-Za-z]+)").matcher(raw);
    checkArgument(matcher.matches(),
        "Value '" + raw + "' must be of the format 1ns, 4mb, etc.");

    Number number = (Number) parser.parse(null, matcher.group(1));
    String unitRaw = matcher.group(2);

    Unit unit = (Unit) checkedGet(Unit.class).parse(null, unitRaw);
    checkArgument(unit.getClass() == paramParsers.get(1), String.format(
        "Unit type (%s) does not match argument type (%s).",
        unit.getClass(), paramParsers.get(1)));

    Class numberClass = paramParsers.get(0);
    if (numberClass == Integer.class) {
      return Amount.of(number.intValue(), unit);
    } else if (numberClass == Double.class) {
      return Amount.of(number.doubleValue(), unit);
    } else if (numberClass == Long.class) {
      return Amount.of(number.longValue(), unit);
    } else if (numberClass == Byte.class) {
      return Amount.of(number.byteValue(), unit);
    } else if (numberClass == Short.class) {
      return Amount.of(number.shortValue(), unit);
    } else if (numberClass == Float.class) {
      return Amount.of(number.floatValue(), unit);
    }

    throw new IllegalArgumentException("Unrecognized number class "
        + numberClass.getCanonicalName());
  }
}
