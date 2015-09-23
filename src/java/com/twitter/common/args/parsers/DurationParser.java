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
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import com.google.common.base.Preconditions;

import com.twitter.common.args.ParserOracle;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

/**
 * Utility class for parsing durations of the form "1d23h59m59s" (as well as subvariants, i.e.
 * "10h5s" would also work, as would "2d"). These values are useful representations in HTTP query
 * parameters for durations.
 *
 */
public class DurationParser extends TypeParameterizedParser<Amount<?, ?>> {

  private static final String SUFFIXES = "dhms";
  private static final Time[] TIME_UNITS = {Time.DAYS, Time.HOURS, Time.MINUTES, Time.SECONDS};

  public DurationParser() {
    super(2);
  }

  @Override
  Amount<?, ?> doParse(ParserOracle parserOracle, String raw, List<Type> paramParsers)
      throws IllegalArgumentException {
    Type secondParamClass = paramParsers.get(1);
    Preconditions.checkArgument(
        secondParamClass == Time.class,
        String.format("Expected %s for "
            + "second type parameter, but got got %s", Time.class.getName(),
            secondParamClass));
    return parse(raw);
  }

  /**
   * Parses a duration of the form "1d23h59m59s" (as well as subvariants, i.e. "10h5s" would also
   * work, as would "2d").
   *
   * @param spec the textual duration specification
   * @return the parsed form
   * @throws IllegalArgumentException if the specification can not be parsed
   */
  public static Amount<Long, Time> parse(String spec) {
    long time = 0L;
    final List<Object> tokens = Collections.list(new StringTokenizer(spec, SUFFIXES, true));
    Preconditions.checkArgument(tokens.size() > 1);
    for (int i = 1; i < tokens.size(); i += 2) {
      final String token = (String) tokens.get(i);
      Preconditions.checkArgument(token.length() == 1, "Too long suffix '%s'", token);
      final int suffixIndex = SUFFIXES.indexOf(token.charAt(0));
      Preconditions.checkArgument(suffixIndex != -1, "Unrecognized suffix '%s'", token);
      try {
        final int value = Integer.parseInt((String) tokens.get(i - 1));
        time += Amount.of(value, TIME_UNITS[suffixIndex]).as(Time.SECONDS);
      } catch (NumberFormatException e) {
        Preconditions.checkArgument(false, "Invalid number %s", tokens.get(i - 1));
      }
    }
    return Amount.of(time, Time.SECONDS);
  }
}
