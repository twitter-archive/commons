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
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.twitter.common.text.tokenizer.RegexTokenizer;

public class TokenizedCharSequenceStreamTest {
  TwitterTokenStream tokenizer = new RegexTokenizer.Builder()
                                                   .setDelimiterPattern(Pattern.compile(" "))
                                                   .build();

  @Test
  public void testWithBackupTokenizer() {
    TwitterTokenStream stream = new TokenizedCharSequenceStream(tokenizer);

    String text = "This is a test #hashtag";
    TokenizedCharSequence tokenized = TokenizedCharSequence.createFrom(text, tokenizer);

    // test with untokenized text
    stream.reset(text);
    assertEquals(ImmutableList.of("This", "is", "a", "test", "#hashtag"), stream.toStringList());

    // test with already tokenized text
    stream.reset(tokenized);
    assertEquals(ImmutableList.of("This", "is", "a", "test", "#hashtag"), stream.toStringList());
  }

  @Test
  public void testWithoutBackupTokenizer() {
    TwitterTokenStream stream = new TokenizedCharSequenceStream();

    String text = "This is a test #hashtag";
    TokenizedCharSequence tokenized = TokenizedCharSequence.createFrom(text, tokenizer);

    // test with already tokenized text
    stream.reset(tokenized);
    assertEquals(ImmutableList.of("This", "is", "a", "test", "#hashtag"), stream.toStringList());

    try {
      // this should throw IllegalArgumentException
      stream.reset(text);
      assertTrue("IllegalArgumentException was not thrown.", false);
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testWithDummyTokenizer() {
    TwitterTokenStream stream = new TokenizedCharSequenceStream(new TwitterTokenStream() {
      @Override
      public boolean incrementToken() {
        throw new IllegalArgumentException("this should not be called!");
      }

      @Override
      public void reset() {
        throw new IllegalArgumentException("this should not be called!");
      }
    });

    String text = "This is a test #hashtag";
    TokenizedCharSequence tokenized = TokenizedCharSequence.createFrom(text, tokenizer);

    // test with already tokenized text
    // this should not throw IllegalArgumentException
    stream.reset(tokenized);
    assertEquals(ImmutableList.of("This", "is", "a", "test", "#hashtag"), stream.toStringList());
  }
}
