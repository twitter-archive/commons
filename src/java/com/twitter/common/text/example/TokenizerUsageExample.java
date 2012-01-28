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

package com.twitter.common.text.example;

import java.util.Iterator;

import org.apache.lucene.util.Attribute;

import com.twitter.common.text.DefaultTextTokenizer;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.TokenizedCharSequence;
import com.twitter.common.text.token.TokenizedCharSequence.Token;
import com.twitter.common.text.token.attribute.CharSequenceTermAttribute;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

/**
 * Annotated example illustrating major features of {@link DefaultTextTokenizer}.
 */
public class TokenizerUsageExample {
  private static final String[] famousTweets = {
      // http://twitter.com/#!/BarackObama/status/992176676
      "We just made history. All of this happened because you gave your time, talent and passion."
        + "All of this happened because of you. Thanks",
      // http://twitter.com/#!/jkrums/status/1121915133
      "http://twitpic.com/135xa - There's a plane in the Hudson."
        + " I'm on the ferry going to pick up the people. Crazy.",
      // http://twitter.com/#!/carlbildt/status/73498110629904384
      "@khalidalkhalifa Trying to get in touch with you on an issue.",
      // http://twitter.com/#!/SHAQ/status/75996821360615425
      "im retiring Video: http://bit.ly/kvLtE3 #ShaqRetires"
  };

  public static void main(String[] args) {
    // This is the canonical way to create a token stream.
    DefaultTextTokenizer tokenizer =
        new DefaultTextTokenizer.Builder().setKeepPunctuation(true).build();
    TokenStream stream = tokenizer.getDefaultTokenStream();

    // We're going to ask the token stream what type of attributes it makes available. "Attributes"
    // can be understood as "annotations" on the original text.
    System.out.println("Attributes available:");
    Iterator<Class<? extends Attribute>> iter = stream.getAttributeClassesIterator();
    while (iter.hasNext()) {
      Class<? extends Attribute> c = iter.next();
      System.out.println(" - " + c.getCanonicalName());
    }
    System.out.println("");

    // We're now going to iterate through a few tweets and tokenize each in turn.
    for (String tweet : famousTweets) {
      // We're first going to demonstrate the "token-by-token" method of consuming tweets.
      System.out.println("Processing: " + tweet);
      // Reset the token stream to process new input.
      stream.reset(tweet);

      // Now we're going to consume tokens from the stream.
      int tokenCnt = 0;
      while (stream.incrementToken()) {
        // CharSequenceTermAttribute holds the actual token text. This is preferred over
        // TermAttribute because it avoids creating new String objects.
        CharSequenceTermAttribute termAttribute = stream
            .getAttribute(CharSequenceTermAttribute.class);

        // TokenTypeAttribute holds, as you'd expect, the type of the token.
        TokenTypeAttribute typeAttribute = stream.getAttribute(TokenTypeAttribute.class);
        System.out.println(String.format("token %2d (%3d, %3d) type: %12s, token: '%s'",
                tokenCnt, termAttribute.getOffset(), termAttribute.getLength() - termAttribute.getOffset(),
                typeAttribute.getType().name, termAttribute.getTermCharSequence()));
        tokenCnt++;
      }
      System.out.println("");

      // We're now going to demonstrate the TokenizedCharSequence API.
      // This should produce exactly the same result as above.
      tokenCnt = 0;
      System.out.println("Processing: " + tweet);
      TokenizedCharSequence tokSeq = tokenizer.tokenize(tweet);
      for (Token tok : tokSeq.getTokens()) {
        System.out.println(String.format("token %2d (%3d, %3d) type: %12s, token: '%s'",
            tokenCnt, tok.getOffset(), tok.getOffset() + tok.getLength(),
            tok.getType().name, tok.getTerm()));
        tokenCnt++;
      }
      System.out.println("");
    }
  }
}
