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
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.text.extractor.RegexExtractor;
import com.twitter.common.text.token.TwitterTokenStream;
import com.twitter.common.text.token.attribute.CharSequenceTermAttribute;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

public class HashtagTokenCombinerTest {
  private TwitterTokenStream stream;
  private CharSequenceTermAttribute termAttr;
  private TokenTypeAttribute typeAttr;

  @Before
  public void setup() {
    stream = new HashtagTokenCombiner(
        // This toknizes text into alphabet-only, number-only tokens
        // and '#', '_'.
        new RegexExtractor.Builder().setRegexPattern(
            Pattern.compile("([0-9]+|[a-zA-Z]+|\\p{InKatakana}+|#|_)")).build());
    termAttr = stream.getAttribute(CharSequenceTermAttribute.class);
    typeAttr = stream.getAttribute(TokenTypeAttribute.class);
  }

  @Test
  public void testSingleHashtag() {
    String text = "this is a #hashtag";
    stream.reset(text);

    verify(ImmutableList.of("this", "is", "a", "#hashtag"),
        new boolean[]{false, false, false, true});
  }

  @Test
  public void testMultipleHashtags() {
    String text = "#this #is #a #hashtag";
    stream.reset(text);
    verify(ImmutableList.of("#this", "#is", "#a", "#hashtag"),
        new boolean[]{true, true, true, true});
  }

  @Test
  public void testHashtagWithMultipleTokens() {
    String text = "#hash_tag_123";
    stream.reset(text);
    verify(ImmutableList.of("#hash_tag_123"), new boolean[]{true});
  }

  @Test
  public void testJAHashtag() {
    String text = "this is now #ハッシュタグ";
    stream.reset(text);
    verify(ImmutableList.of("this", "is", "now", "#ハッシュタグ"),
        new boolean[]{false, false, false, true});
  }

  @Test
  public void testNotHashtag() {
    String text = "this is also not#hashtag";
    stream.reset(text);
    verify(ImmutableList.of("this", "is", "also", "not", "#", "hashtag"),
        new boolean[]{false, false, false, false, false, false});
  }

  private void verify(List<String> tokens, boolean[] isHashtag) {
    for (int i = 0; i < tokens.size(); i++) {
      assertTrue(stream.incrementToken());
      assertEquals(tokens.get(i), termAttr.getTermString());
      assertEquals(isHashtag[i], TokenType.HASHTAG.equals(typeAttr.getType()));
    }
    assertFalse(stream.incrementToken());
  }
}
