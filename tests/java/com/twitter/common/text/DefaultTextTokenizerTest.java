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

package com.twitter.common.text;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class DefaultTextTokenizerTest {
  private DefaultTextTokenizer tokenizerWithoutPunctuation;
  private DefaultTextTokenizer tokenizerWithPunctuation;

  @Before
  public void setup() {
    tokenizerWithoutPunctuation = new DefaultTextTokenizer.Builder().build();
    tokenizerWithPunctuation =
        new DefaultTextTokenizer.Builder().setKeepPunctuation(true).build();
  }

  @Test
  public void testTokenizerBasic() {
    testTokenizerString(tokenizerWithoutPunctuation, "two words", ImmutableList.of("two", "words"));
    testTokenizerString(tokenizerWithoutPunctuation, "two, words.", ImmutableList.of("two", "words"));
  }

  @Test
  public void testENWithURLUserNameHashtag() {
    testTokenizerString(tokenizerWithoutPunctuation,
        "This test has @username and #hashtag and http://twitter.com .",
        ImmutableList.of("This", "test", "has", "@username", "and",
            "#hashtag", "and", "http://twitter.com"));
  }

  @Test
  public void testSmiley() {
    testTokenizerString(tokenizerWithoutPunctuation,
        "smiley :) :D :P haha",
        ImmutableList.of("smiley", ":)", ":D", ":P", "haha"));
  }

  @Test
  public void testPunctuation() {
    testTokenizerString(tokenizerWithoutPunctuation,
        "This has a comma, and also period.",
        ImmutableList.of("This", "has", "a", "comma", "and", "also", "period"));

    testTokenizerString(tokenizerWithPunctuation,
        "This has a comma, and also period.",
        ImmutableList.of("This", "has", "a", "comma", ",", "and", "also", "period", "."));
  }

  @Test
  public void testCombinesContractionsAndTitles() {
    testTokenizerString(tokenizerWithoutPunctuation,
        "Sam's first Penguin test, thanks to Mr. Fujii",
        ImmutableList.of("Sam's", "first", "Penguin", "test", "thanks", "to", "Mr.", "Fujii"));
  }

  private void testTokenizerString(DefaultTextTokenizer tokenizer, String text,
      List<String> expected) {
    List<String> tokens = tokenizer.tokenizeToStrings(text);
    assertEquals(expected, tokens);
  }
}
