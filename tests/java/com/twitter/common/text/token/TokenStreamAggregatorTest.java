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

package com.twitter.common.text.token;

import static org.junit.Assert.assertEquals;

import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.twitter.common.text.tokenizer.RegexTokenizer;

public class TokenStreamAggregatorTest {
  @Test
  public void testAggregation() {
    // tokenize by space
    TwitterTokenStream stream1 = new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile(" ")).build();
    // tokenize by underbar _
    TwitterTokenStream stream2 = new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile("_")).build();

    TwitterTokenStream aggregator = TokenStreamAggregator.of(stream1, stream2);
    aggregator.reset("aa bb_cc dd ee_ff_gg");

    assertEquals(ImmutableList.of("aa", "bb_cc", "dd", "ee_ff_gg", "aa bb", "cc dd ee", "ff", "gg"), aggregator.toStringList());
  }
}
