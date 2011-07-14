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

import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import com.twitter.common.text.token.attribute.CharSequenceTermAttribute;
import com.twitter.common.text.token.attribute.PartOfSpeechAttribute;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

/**
 * Reproduces the result of tokenization if an input text is an instance of
 * TokenizedCharSequence. Otherwise, passes the input text to downstream
 * TokenStream.
 */
public class TokenizedCharSequenceStream extends TokenStream {
  private final TokenStream inputStream;

  private final CharSequenceTermAttribute termAttr;
  private final OffsetAttribute offsetAttr;
  private final TokenTypeAttribute typeAttr;
  private final PartOfSpeechAttribute posAttr;

  private TokenizedCharSequence tokenized = null;
  private int currentIndex = 0;

  /**
   * Constructor.
   * If an input text is not tokenized (is not an instance of TokenizedCharSequence),
   * this uses inputStream to tokenize it.
   *
   * @param inputStream a token stream to tokenize a text if it's not tokenized yet.
   */
  public TokenizedCharSequenceStream(TokenStream inputStream) {
    super(inputStream.cloneAttributes());

    this.inputStream = inputStream;
    termAttr = addAttribute(CharSequenceTermAttribute.class);
    offsetAttr = addAttribute(OffsetAttribute.class);
    typeAttr = addAttribute(TokenTypeAttribute.class);
    if (hasAttribute(PartOfSpeechAttribute.class)) {
      posAttr = getAttribute(PartOfSpeechAttribute.class);
    } else {
      posAttr = null;
    }
  }

  /**
   * Constructor.
   * This can only accept an already-tokenized text (TokenzedCharSequence) as input.
   */
  public TokenizedCharSequenceStream() {
    this.inputStream = null;
    termAttr = addAttribute(CharSequenceTermAttribute.class);
    offsetAttr = addAttribute(OffsetAttribute.class);
    typeAttr = addAttribute(TokenTypeAttribute.class);
    posAttr = addAttribute(PartOfSpeechAttribute.class);
  }

  @Override
  public boolean incrementToken() {
    // If input is already tokenized, reproduce the TokenStream;
    // otherwise, simply pass it onto the downstream TokenStream.

    if (tokenized == null) {
      // Input is not tokenized; let inputStream tokenize it.
      if (!inputStream.incrementToken()) {
        return false;
      }
      restoreState(inputStream.captureState());
      return true;
    }

    if (currentIndex >= tokenized.getTokens().size()) {
      // No more tokens.
      return false;
    }

    TokenizedCharSequence.Token token = tokenized.getTokens().get(currentIndex);

    termAttr.setOffset(token.getOffset());
    termAttr.setLength(token.getLength());
    offsetAttr.setOffset(token.getOffset(), token.getOffset() + token.getLength());
    typeAttr.setType(token.getType());
    if (posAttr != null) {
      posAttr.setPOS(token.getPartOfSpeech());
    }

    currentIndex++;
    return true;
  }

  @Override
  public void reset(CharSequence input) {
    // Check if input is already tokenized or not.
    if (input instanceof TokenizedCharSequence) {
      tokenized = (TokenizedCharSequence) input;
      currentIndex = 0;
      termAttr.setCharSequence(tokenized);
    } else if (inputStream == null) {
      // If no inputStream is provided, throw an exception.
      throw new IllegalArgumentException("Input must be an instance of TokenizedCharSequence"
          + " because there is no TokenStream in the downstream to tokenized a text.");
    } else {
      // Otherwise, let inputStream tokenize the input.
      inputStream.reset(input);
      tokenized = null;
    }
  }
}
