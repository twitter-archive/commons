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

package com.twitter.common.text.extractor;

import java.util.regex.Pattern;

import com.google.common.base.Preconditions;

import com.twitter.common.text.detector.PunctuationDetector;

/**
 * Extracts emoticons (e.g., :), :-( ) from a text.
 */
public class EmoticonExtractor extends RegexExtractor {
  private static final String EMOTICON_DELIMITER =
          PunctuationDetector.SPACE_REGEX + "|" + PunctuationDetector.PUNCTUATION_REGEX;

  public static final Pattern SMILEY_REGEX_PATTERN = Pattern.compile(":[)DdpP]|:[ -]\\)|<3");
  public static final Pattern FROWNY_REGEX_PATTERN = Pattern.compile(":[(<]|:[ -]\\(");
  public static final Pattern EMOTICON_REGEX_PATTERN =
          Pattern.compile("(?<=^|" + EMOTICON_DELIMITER + ")("
            + SMILEY_REGEX_PATTERN.pattern() + "|" + FROWNY_REGEX_PATTERN.pattern()
            + ")+(?=$|" + EMOTICON_DELIMITER + ")");

  /** The term of art for referring to {positive, negative} sentiment is polarity. */
  public enum Polarity {
    HAPPY,
    SAD
  }

  /** Default constructor. **/
  public EmoticonExtractor() {
    setRegexPattern(EMOTICON_REGEX_PATTERN, 1, 1);
  }

  /**
   * Returns the polarity (happy, sad...) of a given emoticon.
   *
   * @param emoticon emoticon text
   * @return polarity of the emoticon
   */
  public static final Polarity getPolarityOf(CharSequence emoticon) {
    Preconditions.checkNotNull(emoticon);
    Preconditions.checkArgument(emoticon.length() > 0);

    char lastChar = emoticon.charAt(emoticon.length() - 1);
    if (lastChar == '(' || lastChar == '<') {
      return Polarity.SAD;
    }

    return Polarity.HAPPY;
  }
}
