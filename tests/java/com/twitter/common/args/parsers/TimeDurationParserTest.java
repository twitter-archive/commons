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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.args.ParserOracle;
import com.twitter.common.args.Parsers;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Unit;
import com.twitter.util.Duration;

/**
 * Test that the TimeDurationParser behaves as expected.
 * @author Ugo Di Girolamo
 */
public class TimeDurationParserTest {
  private TimeDurationParser parser = new TimeDurationParser();
  private ParserOracle parserOracle;

  @Before
  public void init() throws IOException {
    parserOracle =
        new Parsers(ImmutableMap.of(
            Long.class, new LongParser(),
            Unit.class, new UnitParser(),
            Amount.class, new AmountParser()));
  }

  private Duration parse(String raw) {
    return parser.parse(parserOracle, Duration.class, raw);
  }

  @Test
  public void testParseValidDurations() {
    Assert.assertEquals(11, parse("11secs").inSeconds());
    Assert.assertEquals(21, parse("21days").inDays());
  }

  @Test
  public void testParseInvalidDurations() {
    List<String> invalidDurationStrings = Arrays.asList(
        "11.1secs",  // only int values
        "11secondi",  // secondi is not a valid string
        "1000", // no unit
        "onesecs", // we definitely don't try to parse spelled out numbers
        "secs"  // missing value
    );
    for (String testString : invalidDurationStrings) {
      try {
        parse(testString);
        Assert.fail("[" + testString + "] is an invalid duration string.");
      } catch (IllegalArgumentException e) {
        // Expected
      }
    }
  }
}
