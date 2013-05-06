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
import com.twitter.common.text.token.attribute.TokenType;

/**
 * Combiner that looks ahead to the next token and combines it with the current token based on
 * specified conditions.
 */
public abstract class LookAheadTokenCombiner extends TokenProcessor {
  private TokenType type = TokenType.TOKEN;

  private State nextState = null;

  public LookAheadTokenCombiner(TokenStream inputStream) {
    super(inputStream);
  }

  @Override
  public boolean incrementToken() {
    if (nextState != null) {
      restoreState(nextState);
      nextState = null;
      return true;
    }

    if (!incrementInputStream()) {
      return false;
    }

    // check if current token is contracted word
    if (canBeCombinedWithNextToken(term())) {
      // save the current state, offset and length
      State state = captureState();
      int offset = offset();
      int length = length();

      // check if the next token is comma or not.
      if (incrementInputStream()) {
        nextState = captureState();

        CharSequence term = term();
        if (canBeCombinedWithPreviousToken(term)) {
          // combine with previous token
          restoreState(state);
          updateOffsetAndLength(offset, length + term.length());
          updateType(type);

          nextState = null;
          return true;
        }
      }
      restoreState(state);
    }

    return true;
  }

  protected void setType(TokenType type) {
    this.type = type;
  }

  public abstract boolean canBeCombinedWithNextToken(CharSequence term);

  public abstract boolean canBeCombinedWithPreviousToken(CharSequence term);
}
