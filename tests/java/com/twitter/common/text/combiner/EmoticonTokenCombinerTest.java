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

package com.twitter.common.text.combiner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.text.token.TwitterTokenStream;
import com.twitter.common.text.token.attribute.CharSequenceTermAttribute;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;
import com.twitter.common.text.tokenizer.LatinTokenizer;

public class EmoticonTokenCombinerTest {
  private TwitterTokenStream stream;
  private CharSequenceTermAttribute termAttr;
  private TokenTypeAttribute typeAttr;

  @Before
  public void setup() {
    stream = new EmoticonTokenCombiner(
        new LatinTokenizer.Builder().setKeepPunctuation(true).build());
    termAttr = stream.getAttribute(CharSequenceTermAttribute.class);
    typeAttr = stream.getAttribute(TokenTypeAttribute.class);
  }

  @Test
  public void test() {
    stream.reset("this is a smiley :)");
    verify(ImmutableList.of("this", "is", "a", "smiley", ":)"),
        TokenType.TOKEN, TokenType.TOKEN, TokenType.TOKEN, TokenType.TOKEN, TokenType.EMOTICON);

    stream.reset("sad smiley :-(");
    verify(ImmutableList.of("sad", "smiley", ":-("),
        TokenType.TOKEN, TokenType.TOKEN, TokenType.EMOTICON);

    stream.reset("smiley with space : )");
    verify(ImmutableList.of("smiley", "with", "space", ": )"),
        TokenType.TOKEN, TokenType.TOKEN, TokenType.TOKEN, TokenType.EMOTICON);

    stream.reset("First smiley. :p Second smiley :D False smiley :((");
    verify(ImmutableList.of("First", "smiley", ".", ":p", "Second", "smiley", ":D", "False", "smiley", ":(", "("),
        TokenType.TOKEN, TokenType.TOKEN, TokenType.PUNCTUATION, TokenType.EMOTICON,
        TokenType.TOKEN, TokenType.TOKEN, TokenType.EMOTICON,
        TokenType.TOKEN, TokenType.TOKEN, TokenType.EMOTICON, TokenType.PUNCTUATION);
  }

  private void verify(List<String> tokens, TokenType... types) {
    for(int i = 0; i < tokens.size(); i++) {
      assertTrue(stream.incrementToken());
      assertEquals(tokens.get(i), termAttr.getTermString());
      assertEquals(types[i], typeAttr.getType());
    }
    assertFalse(stream.incrementToken());
  }
}
