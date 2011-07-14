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

import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.junit.Test;

import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;
import com.twitter.common.text.tokenizer.RegexTokenizer;

public class PunctuationDetectorTest {
  @Test
  public void testNoPunctuationDetector() {
    // This test case shows that, without a punctuation detector, the punctuation characters do not
    // have the correct token type.
    TokenStream stream =
        new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile(" ")).build();
    stream.reset("When I was young , I liked insects .");

    TokenTypeAttribute typeAttr = stream.getAttribute(TokenTypeAttribute.class);

    int cnt = 0;
    while (stream.incrementToken()) {
      assertFalse(TokenType.PUNCTUATION.equals(typeAttr.getType()));
      cnt++;
    }
    // Make sure we've consumed the correct number of tokens.
    assertEquals(9, cnt);
  }

  @Test
  public void testPunctuationDetector() {
    // Compare with testNoPunctuationDetector(): now we add a punctuation detector, and the
    // punctuation characters have the correct types.
    TokenStream mockStream =
      new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile(" ")).build();
    mockStream.reset("When I was young , I liked insects .");

    PunctuationDetector stream = new PunctuationDetector(mockStream);
    TermAttribute termAttr = stream.getAttribute(TermAttribute.class);
    TokenTypeAttribute typeAttr = stream.getAttribute(TokenTypeAttribute.class);

    int cnt = 0;
    while (stream.incrementToken()) {
      if (",".equals(termAttr.term()) || ".".equals(termAttr.term())) {
        assertEquals(typeAttr.getType(), TokenType.PUNCTUATION);
      } else {
        assertFalse(TokenType.PUNCTUATION.equals(typeAttr.getType()));
      }
      cnt++;
    }
    assertEquals(9, cnt);

    // Additional examples in jp:
    stream.reset("「　今日 は いい 天気 、 明日 も いい 天気 。 」");

    cnt = 0;
    while (stream.incrementToken()) {
      if (ImmutableSet.of("[", "、", "。", "」").contains(termAttr.term())) {
        assertEquals(typeAttr.getType(), TokenType.PUNCTUATION);
      } else {
        assertFalse(TokenType.PUNCTUATION.equals(typeAttr.getType()));
      }
      cnt++;
    }
    assertEquals(11, cnt);
  }

  @Test
  public void testAllPunctuation() {
    TokenStream mockStream =
        new RegexTokenizer.Builder().setDelimiterPattern(Pattern.compile(" ")).build();
    mockStream.reset("When I was young , I liked insects .");

    PunctuationDetector stream = new PunctuationDetector(mockStream);
    TokenTypeAttribute typeAttr = stream.getAttribute(TokenTypeAttribute.class);

    // Variations of middle dots.
    stream.reset("· · • ∙ ⋅ ・ ･ ● ○ ◎");
    int cnt = 0;
    while (stream.incrementToken()) {
      assertEquals(TokenType.PUNCTUATION, typeAttr.getType());
      cnt++;
    }
    assertEquals(10, cnt);
  }
}
