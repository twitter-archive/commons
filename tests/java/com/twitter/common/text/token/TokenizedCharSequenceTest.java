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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import com.twitter.common.text.token.TokenizedCharSequence.Token;
import com.twitter.common.text.token.attribute.TokenType;

public class TokenizedCharSequenceTest {
  @Test(expected=NullPointerException.class)
  public void testNullConstructor() {
    new TokenizedCharSequence.Builder(null).build();
  }

  @Test
  public void testEmptyConstructor() {
    // it is OK to create TokenizedCharSequence with empty text.
    new TokenizedCharSequence.Builder("").build();
  }

  @Test
  public void testTokenizedCharSequence() {
    // exactly same contents
    String text = "test test";
    TokenizedCharSequence text1 = new TokenizedCharSequence.Builder(text).addToken(0, 4).addToken(5, 4).build();
    TokenizedCharSequence text2 = new TokenizedCharSequence.Builder(text).addToken(0, 4).addToken(5, 4).build();

    assertTrue(text1 != text2);
    assertEquals(text1, text2);
    assertEquals(text1.toString(), text2.toString());
    assertEquals(text1.hashCode(), text2.hashCode());

    // different contents
    text = "test test test";
    TokenizedCharSequence text3 = new TokenizedCharSequence.Builder(text).addToken(0, 4).addToken(5, 4).addToken(10, 4).build();
    assertFalse(text1.equals(text3));
    assertFalse(text1.toString().equals(text3.toString()));
    assertFalse(text1.hashCode() == text3.hashCode());

    // same contents but not String
    StringBuffer buf = new StringBuffer("test test");
    TokenizedCharSequence text4 = new TokenizedCharSequence.Builder(buf).addToken(0, 4).addToken(5, 4).build();
    assertTrue(text1 != text4);
    assertEquals(text1, text4);
    assertEquals(text1.toString(), text4.toString());
    assertEquals(text1.hashCode(), text4.hashCode());
  }

  @Test
  public void testGetTokensOf() {
    String text = "test, #hashtag, @username.";
    TokenizedCharSequence tokenized = new TokenizedCharSequence.Builder(text)
      .addToken(0, 4, TokenType.TOKEN)
      .addToken(4, 1, TokenType.PUNCTUATION)
      .addToken(6, 8, TokenType.HASHTAG)
      .addToken(14, 1, TokenType.PUNCTUATION)
      .addToken(16, 9, TokenType.USERNAME)
      .addToken(25, 1, TokenType.PUNCTUATION)
      .build();

    assertEquals(3, tokenized.getTokensOf(TokenType.TOKEN, TokenType.HASHTAG, TokenType.USERNAME).size());
    assertEquals(3, tokenized.getTokensOf(TokenType.PUNCTUATION).size());

    List<String> hashtags = tokenized.getTokenStringsOf(TokenType.HASHTAG);
    assertEquals(ImmutableList.of("#hashtag"), hashtags);

    List<String> hash_user = tokenized.getTokenStringsOf(TokenType.HASHTAG, TokenType.USERNAME);
    assertEquals(ImmutableList.of("#hashtag", "@username"), hash_user);
  }

  @Test
  public void testTokenizeToken() {
    String text = "abCDef";
    TokenizedCharSequence tokenized = new TokenizedCharSequence.Builder(text)
            .addToken(0, 2, TokenType.TOKEN)
            .addToken(2, 2, TokenType.TOKEN)
            .addToken(4, 2, TokenType.TOKEN)
            .build();

    List<Token> tokens = tokenized.getTokens();
    assertEquals(3, tokens.size());

    // tokenize "CD" into "C" and "D"
    Token tokenC = tokens.get(1).tokenize(0, 1);
    Token tokenD = tokens.get(1).tokenize(1, 1);

    assertEquals("C", tokenC.toString());
    assertEquals(2, tokenC.getOffset());
    assertEquals(1, tokenC.getLength());
    assertEquals("D", tokenD.toString());
    assertEquals(3, tokenD.getOffset());
    assertEquals(1, tokenD.getLength());
  }
}
