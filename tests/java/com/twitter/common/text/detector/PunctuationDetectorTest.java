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

package com.twitter.common.text.detector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import com.twitter.common.text.token.TwitterTokenStream;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.tokenizer.RegexTokenizer;

public class PunctuationDetectorTest {
  @Test
  public void testNoPunctuationDetector() {
    // This test case shows that, without a punctuation detector, the punctuation characters do not
    // have the correct token type.
    TwitterTokenStream stream =
        new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile(" ")).build();
    stream.reset("When I was young , I liked insects .");

    int cnt = 0;
    while (stream.incrementToken()) {
      assertFalse(TokenType.PUNCTUATION.equals(stream.type()));
      cnt++;
    }
    // Make sure we've consumed the correct number of tokens.
    assertEquals(9, cnt);
  }

  @Test
  public void testPunctuationDetector() {
    // Compare with testNoPunctuationDetector(): now we add a punctuation detector, and the
    // punctuation characters have the correct types.
    TwitterTokenStream regexTokenizerStream =
      new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile(" ")).build();
    regexTokenizerStream.reset("When I was young , I liked insects .");

    PunctuationDetector stream = new PunctuationDetector.Builder(regexTokenizerStream).build();

    int cnt = 0;
    while (stream.incrementToken()) {
      String token = stream.term().toString();
      if (",".equals(token) || ".".equals(token)) {
        assertEquals(TokenType.PUNCTUATION, stream.type());
      } else {
        assertFalse(TokenType.PUNCTUATION.equals(stream.type()));
      }
      cnt++;
    }
    assertEquals(9, cnt);

    // Additional examples in jp:
    stream.reset("「　今日 は いい 天気 、 明日 も いい 天気 。 」");

    cnt = 0;
    while (stream.incrementToken()) {
      if (ImmutableSet.of("[", "、", "。", "」").contains(stream.term().toString())) {
        assertEquals(stream.type(), TokenType.PUNCTUATION);
      } else {
        assertFalse(TokenType.PUNCTUATION.equals(stream.type()));
      }
      cnt++;
    }
    assertEquals(11, cnt);
  }

  @Test
  public void testAllPunctuation() {
    TwitterTokenStream regexTokenizerStream =
        new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile(" ")).build();
    regexTokenizerStream.reset("When I was young , I liked insects .");

    PunctuationDetector stream = new PunctuationDetector.Builder(regexTokenizerStream).build();

    // Variations of middle dots.
    stream.reset("· · • ∙ ⋅ ・ ･ ● ○ ◎");
    int cnt = 0;
    while (stream.incrementToken()) {
      assertEquals(TokenType.PUNCTUATION, stream.type());
      cnt++;
    }
    assertEquals(10, cnt);
  }

  @Test
  public void testNewlineIsPunctuation() {
    TwitterTokenStream regexTokenizerStream =
        new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile(" ")).build();
    regexTokenizerStream.reset("Newline \n as punctuation");
    PunctuationDetector stream = new PunctuationDetector.Builder(regexTokenizerStream).build();
    int cnt = 0;
    while (stream.incrementToken()) {
      if (stream.term().toString().equals("\n")) {
        cnt++;
        assertEquals(TokenType.PUNCTUATION, stream.type());
      }
    }
    assertEquals(1, cnt);
  }

  @Test
  public void testCombiningMarks() {
    TwitterTokenStream regexTokenizerStream =
            new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile(" ")).build();
    // escaped sequence for "word ́ ̋ ̔ ̛ ̧ word ِ ٓ ा ै ิ word"
    regexTokenizerStream.reset("word \u0301 \u030b \u0314 \u031b \u0327 word \u0650 \u0653 \u093e \u0948 \u0e34 word");

    // Test behavior with regard to combining marks in various languages
    PunctuationDetector stream = new PunctuationDetector.Builder(regexTokenizerStream).build();

    int cnt = 0;
    while (stream.incrementToken()) {
      if ("word".equals(stream.term().toString())) {
        assertFalse(TokenType.PUNCTUATION.equals(stream.type()));
      } else {
        assertEquals(TokenType.PUNCTUATION, stream.type());
      }
      cnt++;
    }
    assertEquals(13, cnt);

    // Test with combining marks not treated as punctuation
    regexTokenizerStream.reset();
    stream = new PunctuationDetector.Builder(regexTokenizerStream)
            .useCombiningMarks(false)
            .build();

    cnt = 0;
    while (stream.incrementToken()) {
      assertFalse(TokenType.PUNCTUATION.equals(stream.type()));
      cnt++;
    }
    assertEquals(13, cnt);
  }
}
