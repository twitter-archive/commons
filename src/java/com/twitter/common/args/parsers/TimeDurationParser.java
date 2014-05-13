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

import com.google.common.reflect.TypeToken;

import com.twitter.common.args.ArgParser;
import com.twitter.common.args.Parser;
import com.twitter.common.args.ParserOracle;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.util.Duration;


/**
 * A parser for Duration objects.
 *
 * Accepts in input the same format as the Amount&lt;Long, Time&gt; parser,
 * &lt;number&gt;&lt;unit&gt; i.e. 10ns, 1days
 *
 * @author Ugo Di Girolamo
 */
@ArgParser
public class TimeDurationParser implements Parser<Duration> {
  private static final Type TIME_AMOUNT_TYPE = new TypeToken<Amount<Long, Time>>() { }
      .getType();

  @Override
  public Duration parse(ParserOracle parserOracle, Type type, String raw)
      throws IllegalArgumentException {
    Parser<?> amountParser = parserOracle.get(TypeToken.of(Amount.class));

    @SuppressWarnings("unchecked")
    Amount<Long, Time> parsed = (Amount<Long, Time>)
        amountParser.parse(parserOracle, TIME_AMOUNT_TYPE, raw);
    return Duration.fromTimeUnit(parsed.getValue(), parsed.getUnit().getTimeUnit());
  }
}
