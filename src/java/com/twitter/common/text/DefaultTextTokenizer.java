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

import java.util.List;

import com.google.common.base.Preconditions;

import com.twitter.common.text.combiner.DotContractedTokenCombiner;
import com.twitter.common.text.combiner.EmoticonTokenCombiner;
import com.twitter.common.text.combiner.HashtagTokenCombiner;
import com.twitter.common.text.combiner.PossessiveTokenCombiner;
import com.twitter.common.text.combiner.StockTokenCombiner;
import com.twitter.common.text.combiner.URLTokenCombiner;
import com.twitter.common.text.combiner.UserNameTokenCombiner;
import com.twitter.common.text.example.TokenizerUsageExample;
import com.twitter.common.text.filter.PunctuationFilter;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.TokenizedCharSequence;
import com.twitter.common.text.token.TokenizedCharSequenceStream;
import com.twitter.common.text.tokenizer.LatinTokenizer;

/**
 * Default implementation of a tokenizer for processing tweets. For sample usage, please consult
 * annotated code example in {@link TokenizerUsageExample}.
 */
public class DefaultTextTokenizer {
  private TokenStream tokenizationStream =
      new TokenizedCharSequenceStream(applyDefaultChain(new LatinTokenizer.Builder().build()));

  // use Builder.
  private DefaultTextTokenizer() { }

  public static final TokenStream applyDefaultChain(TokenStream tokenizer) {
    return
      // combine stock symbol
      new StockTokenCombiner(
        // combine emoticon like :) :-D
        new EmoticonTokenCombiner(
          // combine Mr/Mrs/Dr/Inc + .
          new DotContractedTokenCombiner(
            // combine possessive form (e.g., apple's)
            new PossessiveTokenCombiner(
              // combine URL
              new URLTokenCombiner(
                // combine # + hashtag
                new HashtagTokenCombiner(
                  // combine @ + user name
                  new UserNameTokenCombiner(tokenizer)))))));
  }

  /**
   * Returns {@code TokenStream} to tokenize a text.
   *
   * @return {@code TokenStream} to tokenize the text
   */
  public TokenStream getDefaultTokenStream() {
    return tokenizationStream;
  }

  /**
   * Tokenizes a {@code CharSequence}, and returns a {@code TokenizedCharSequence} as a result.
   *
   * @param input text to be tokenized
   * @return {@code TokenizedCharSequence} instance
   */
  public TokenizedCharSequence tokenize(CharSequence input) {
    Preconditions.checkNotNull(input);
    return TokenizedCharSequence.createFrom(input, getDefaultTokenStream());
  }

  /**
   * Tokenizes a {@code CharSequence} into a list of Strings.
   *
   * @param input text to be tokenized
   * @return a list of tokens as String objects
   */
  public List<String> tokenizeToStrings(CharSequence input) {
    Preconditions.checkNotNull(input);
    TokenStream tokenizer = getDefaultTokenStream();
    tokenizer.reset(input);
    return tokenizer.toStringList();
  }

  /**
   * Builder for {@link DefaultTextTokenizer}
   */
  public static final class Builder {
    private final DefaultTextTokenizer tokenizer = new DefaultTextTokenizer();
    private boolean keepPunctuation = false;

    /**
     * Set to {@code true} to retain punctuation in output.
     *
     * @param keepPunctuation {@code true} to retain punctuation
     * @return this Builder
     */
    public Builder setKeepPunctuation(boolean keepPunctuation) {
      this.keepPunctuation = keepPunctuation;
      return this;
    }

    public DefaultTextTokenizer build() {
      if (!keepPunctuation) {
        tokenizer.tokenizationStream =
            new PunctuationFilter(tokenizer.tokenizationStream);
      }

      return tokenizer;
    }
  }
}
