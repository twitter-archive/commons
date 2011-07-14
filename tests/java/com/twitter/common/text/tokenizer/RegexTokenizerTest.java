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

package com.twitter.common.text.tokenizer;

import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class RegexTokenizerTest {
  RegexTokenizer spaceTokenizer =
    new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile("\\s+")).setKeepPunctuation(false).build();

  @Test
  public void testTokenization() {
    String input = "This is a test";
    spaceTokenizer.reset(input);
    assertEquals(ImmutableList.of("This", "is", "a", "test"), spaceTokenizer.toStringList());

    input = "This is a test   ";
    spaceTokenizer.reset(input);
    assertEquals(ImmutableList.of("This", "is", "a", "test"), spaceTokenizer.toStringList());

    input = "  This is a test   ";
    spaceTokenizer.reset(input);
    assertEquals(ImmutableList.of("This", "is", "a", "test"), spaceTokenizer.toStringList());
  }

  @Test
  public void testKeepDelimiter() {
    spaceTokenizer.setKeepPunctuation(true);

    String input = "This is a test";
    spaceTokenizer.reset(input);
    assertEquals(ImmutableList.of("This", " ", "is", " ", "a", " ", "test"), spaceTokenizer.toStringList());

    input = "This is a test   ";
    spaceTokenizer.reset(input);
    assertEquals(ImmutableList.of("This", " ", "is", " ", "a", " ", "test", "   "), spaceTokenizer.toStringList());

    input = "  This is a test   ";
    spaceTokenizer.reset(input);
    assertEquals(ImmutableList.of("  ", "This", " ", "is", " ", "a", " ", "test", "   "), spaceTokenizer.toStringList());
  }
}
