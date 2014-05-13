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

import com.twitter.common.text.token.TwitterTokenStream;
import com.twitter.common.text.token.attribute.TokenType;

/**
 * Updates {@code TokenTypeAttribute} of a token to {@code TokenType.PUNCTUATION}
 * if the token is identified as punctuation.
 */
public class PunctuationDetector extends RegexDetector {
  // Newlines in tweets function as punctuation
  private static final String SPACE_EXCEPTIONS = "\\n\\r";
  public static final String SPACE_CHAR_CLASS = "\\p{C}\\p{Z}&&[^" + SPACE_EXCEPTIONS + "\\p{Cs}]";
  public static final String SPACE_REGEX = "[" + SPACE_CHAR_CLASS + "]";

  public static final String PUNCTUATION_CHAR_CLASS = "\\p{P}\\p{M}\\p{S}" + SPACE_EXCEPTIONS;
  public static final String PUNCTUATION_REGEX = "[" + PUNCTUATION_CHAR_CLASS + "]";
  private static final Pattern DEFAULT_PUNCTUATION_PATTERN = Pattern.compile(PUNCTUATION_REGEX);

  public static final String PUNCTUATION_CHAR_CLASS_WITHOUT_COMBINING_MARKS = "\\p{P}\\p{S}" + SPACE_EXCEPTIONS;
  public static final String PUNCTUATION_REGEX_WITHOUT_COMBINING_MARKS =
          "[" + PUNCTUATION_CHAR_CLASS_WITHOUT_COMBINING_MARKS + "]";
  private static final Pattern PUNCTUATION_PATTERN_WITHOUT_COMBINING_MARKS =
          Pattern.compile(PUNCTUATION_REGEX_WITHOUT_COMBINING_MARKS);

  private PunctuationDetector(TwitterTokenStream inputStream) {
    super(inputStream);
    setRegexPattern(DEFAULT_PUNCTUATION_PATTERN);
    setType(TokenType.PUNCTUATION);
  }

  public void detectCombiningMarksAsPunctuation(boolean useCombiningMarks) {
    if (useCombiningMarks) {
      setRegexPattern(DEFAULT_PUNCTUATION_PATTERN);
    } else {
      setRegexPattern(PUNCTUATION_PATTERN_WITHOUT_COMBINING_MARKS);
    }
  }

  public static class Builder extends AbstractBuilder<PunctuationDetector, Builder> {
    private boolean useCombiningMarks = true;

    public Builder(TwitterTokenStream inputStream) {
      super(new PunctuationDetector(inputStream));
    }

    Builder useCombiningMarks(boolean useCombiningMarks) {
      this.useCombiningMarks = useCombiningMarks;
      return this;
    }

    @Override
    public PunctuationDetector build() {
      PunctuationDetector detector = detector();
      detector.detectCombiningMarksAsPunctuation(useCombiningMarks);
      return detector;
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
