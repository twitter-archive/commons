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

package com.twitter.common.text.tokenizer;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.lucene.util.AttributeSource;

import com.twitter.common.text.detector.PunctuationDetector;

/**
 * Tokenizes text written in Latin alphabets such as English, French, German.
 */
public class LatinTokenizer extends RegexTokenizer {
  // delimiter = one or more space, or one or more punctuation followed by space.
  private static final String DELIMITER = "(?:" + PunctuationDetector.SPACE_REGEX + "+)|("
      + PunctuationDetector.PUNCTUATION_REGEX + ")" + PunctuationDetector.SPACE_REGEX + "*";
  private static final int PATTERN_FLAGS =
    Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ | Pattern.DOTALL;
  private static final Pattern SPLIT_PATTERN = Pattern.compile(DELIMITER, PATTERN_FLAGS);
  private static final int PUNCTUATION_GROUP = 1;

  // Please use Builder
  protected LatinTokenizer() {
  }

  protected LatinTokenizer(AttributeSource attributeSource) {
    super(attributeSource);
  }

  public static final class Builder extends AbstractBuilder<LatinTokenizer, Builder> {
    public Builder() {
      setDelimiterPattern(SPLIT_PATTERN);
      setPunctuationGroupInDelimiterPattern(PUNCTUATION_GROUP);
      setKeepPunctuation(true);
    }

    @Override
    protected LatinTokenizer buildTokenizer(@Nullable AttributeSource attributeSource) {
      if (attributeSource == null) {
        return new LatinTokenizer();
      } else {
        return new LatinTokenizer(attributeSource);
      }
    }
  }
}
