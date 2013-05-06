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

import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import com.twitter.common.text.token.attribute.PartOfSpeechAttribute;
import com.twitter.common.text.token.attribute.TokenGroupAttribute;
import com.twitter.common.text.token.attribute.TokenGroupAttributeImpl;

/**
 * Reproduces the result of tokenization if an input text is an instance of
 * TokenizedCharSequence. Otherwise, passes the input text to downstream
 * TokenStream.
 */
public class TokenizedCharSequenceStream extends TokenProcessor {
  private final PartOfSpeechAttribute posAttr;
  private final PositionIncrementAttribute incAttr;
  private final TokenGroupAttributeImpl groupAttr;

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
    super(inputStream);

    if (hasAttribute(PartOfSpeechAttribute.class)) {
      posAttr = getAttribute(PartOfSpeechAttribute.class);
    } else {
      posAttr = null;
    }
    if (hasAttribute(PositionIncrementAttribute.class)) {
      incAttr = getAttribute(PositionIncrementAttribute.class);
    } else {
      incAttr = null;
    }
    if (hasAttribute(TokenGroupAttribute.class)) {
      groupAttr = (TokenGroupAttributeImpl) getAttribute(TokenGroupAttribute.class);
    } else {
      groupAttr = null;
    }
  }

  /**
   * Constructor.
   * This can only accept an already-tokenized text (TokenzedCharSequence) as input.
   */
  public TokenizedCharSequenceStream() {
    super(new TokenStream() {
      @Override
      public boolean incrementToken() {
        return false;
      }

      @Override
      public void reset(CharSequence input) {
        // If no inputStream is provided, throw an exception.
        throw new IllegalArgumentException("Input must be an instance of TokenizedCharSequence"
                + " because there is no TokenStream in the downstream to tokenized a text.");
      }
    });

    posAttr = addAttribute(PartOfSpeechAttribute.class);
    incAttr = addAttribute(PositionIncrementAttribute.class);
    groupAttr = (TokenGroupAttributeImpl) addAttribute(TokenGroupAttribute.class);
  }

  @Override
  public boolean incrementToken() {
    // If input is already tokenized, reproduce the TokenStream;
    // otherwise, simply pass it onto the downstream TokenStream.

    if (tokenized == null) {
      // Input is not tokenized; let inputStream tokenize it.
      return incrementInputStream();
    }

    if (currentIndex >= tokenized.getTokens().size()) {
      // No more tokens.
      return false;
    }

    TokenizedCharSequence.Token token = tokenized.getTokens().get(currentIndex);

    updateOffsetAndLength(token.getOffset(), token.getLength());
    updateType(token.getType());
    if (posAttr != null) {
      posAttr.setPOS(token.getPartOfSpeech());
    }
    if (incAttr != null) {
      incAttr.setPositionIncrement(token.getPositionIncrement());
    }
    if (groupAttr != null) {
      groupAttr.setSequence(token.getGroup());
    }

    currentIndex++;
    return true;
  }

  @Override
  public void reset(CharSequence input) {
    // Check if input is already tokenized or not.
    if (input instanceof TokenizedCharSequence) {
      clearAttributes();
      tokenized = (TokenizedCharSequence) input;
      currentIndex = 0;
      updateInputCharSequence(tokenized);
    } else {
      // Otherwise, let inputStream tokenize the input.
      super.reset(input);
      tokenized = null;
    }
  }
}
