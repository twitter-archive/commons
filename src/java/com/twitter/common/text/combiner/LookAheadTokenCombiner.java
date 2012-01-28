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

import com.twitter.common.text.token.TokenProcessor;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.CharSequenceTermAttribute;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

/**
 * Combiner that looks ahead to the next token and combines it with the current token based on
 * specified conditions.
 */
public abstract class LookAheadTokenCombiner extends TokenProcessor {
  private TokenType type = TokenType.TOKEN;

  private final CharSequenceTermAttribute inputTermAttr;

  private final CharSequenceTermAttribute termAttr;
  private final TokenTypeAttribute typeAttr;

  private State nextState = null;

  public LookAheadTokenCombiner(TokenStream inputStream) {
    super(inputStream);

    inputTermAttr = inputStream.getAttribute(CharSequenceTermAttribute.class);

    termAttr = getAttribute(CharSequenceTermAttribute.class);
    typeAttr = getAttribute(TokenTypeAttribute.class);
  }

  @Override
  public boolean incrementToken() {
    if (nextState != null) {
      restoreState(nextState);
      nextState = null;
      return true;
    }

    if (!getInputStream().incrementToken()) {
      return false;
    }

    restoreState(getInputStream().captureState());

    // check if current token is contracted word
    if (canBeCombinedWithNextToken(inputTermAttr.getTermCharSequence())) {
      // check if the next token is comma or not.
      if (getInputStream().incrementToken()) {
        nextState = getInputStream().captureState();

        CharSequence term = inputTermAttr.getTermCharSequence();
        if (canBeCombinedWithPreviousToken(term)) {
          // combine
          termAttr.setLength(termAttr.getLength() + term.length());
          typeAttr.setType(type);

          nextState = null;
        }
      }
    }

    return true;
  }

  protected void setType(TokenType type) {
    this.type = type;
  }

  public abstract boolean canBeCombinedWithNextToken(CharSequence term);

  public abstract boolean canBeCombinedWithPreviousToken(CharSequence term);
}
