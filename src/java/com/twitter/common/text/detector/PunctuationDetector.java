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

package com.twitter.common.text.detector;

import java.util.regex.Pattern;

import com.google.common.base.Preconditions;

import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.regex.Spaces;

/**
 * Updates {@code TokenTypeAttribute} of a token to {@code TokenType.PUNCTUATION}
 * if the token is identified as punctuation.
 */
public class PunctuationDetector extends RegexDetector {
  public static final String SPACE_REGEX = "[" + Spaces.getCharacterClass() + "\\s&&[^\\n]]";
  public static final String PUNCTUATION_REGEX = "[\"\',.?!;:()@#&\\\\/*<>\\[\\]{}|=+\\-\\^%$`~"
    + "\u3001-\u3003\u3008-\u3011\u3014-\u301f\uff01-\uff02\uff06-\uff09\uff0c\uff0e\uff1a-\uff1f"
    + "\uff5c\uffe8\u30fb\uff65\u22c5\u2219\u2022\u00b7\u0387\\p{InMiscellaneousSymbols}\\p{InGeometricShapes}\\n]";

  private static final Pattern DEFAULT_PUNCTUATION_REGEX = Pattern.compile(PUNCTUATION_REGEX);

  protected PunctuationDetector(TokenStream inputStream) {
    super(inputStream);
    setRegexPattern(DEFAULT_PUNCTUATION_REGEX);
    setType(TokenType.PUNCTUATION);
  }

  public static class Builder extends AbstractBuilder<PunctuationDetector, Builder> {
    public Builder(TokenStream inputStream) {
      super(new PunctuationDetector(inputStream));
    }
  }

  public abstract static class
      AbstractBuilder<N extends PunctuationDetector, T extends AbstractBuilder<N, T>> {
    private final N detector;

    protected AbstractBuilder(N detector) {
      this.detector = Preconditions.checkNotNull(detector);
    }

    @SuppressWarnings("unchecked")
    protected T self() {
      return (T) this;
    }

    protected N detector() {
      return detector;
    }

    public N build() {
      return detector;
    }
  }
}
