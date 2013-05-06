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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;

import com.twitter.common.text.token.TokenStream;

/**
 * Extracts entities from text according to a given regular expression.
 */
public class RegexExtractor extends TokenStream {
  private Pattern regexPattern;
  private int startGroup = 0;
  private int endGroup = 0;
  private char triggeringChar = 0;
  private Matcher matcher = null;


  /**
   * Protected constructor for subclass builders, clients should use a builder to create an
   * instance.
   */
  protected RegexExtractor() { }

  /**
   * Sets the regular expression used in this {@code RegexExtractor}.
   *
   * @param pattern regular expression defining the entities to be extracted
   */
  protected void setRegexPattern(Pattern pattern) {
    this.regexPattern = pattern;
  }

  /**
   * Sets the regular expression and start/end group ID used in this {@code RegexExtractor}.
   *
   * @param pattern Regex pattern of a substring to be replaced.
   * @param startGroup ID of the group in the pattern that matches the beginning
   *  of the substring being replaced. Set to 0 to match the entire pattern.
   * @param endGroup ID of the group in the pattern that matches the end
   *  of the substring being replaced. Set to 0 to match the entire pattern.
   */
  protected void setRegexPattern(Pattern pattern, int startGroup, int endGroup) {
    this.regexPattern = pattern;
    this.startGroup = startGroup;
    this.endGroup = endGroup;
  }

  /**
   * Sets a character that must appear in the input text. If a specified character does not appear
   * in the input text, this {@code RegexExtractor} does not extract entities from the text.
   * Specifying a {@code triggeringChar} may improve the performance by skipping unnecessary pattern
   * matching.
   *
   * @param triggeringChar a character that must appear in the text
   */
  protected void setTriggeringChar(char triggeringChar) {
    Preconditions.checkNotNull(triggeringChar);
    this.triggeringChar = triggeringChar;
  }

  /**
   * Reset the extractor to use a new {@code CharSequence} as input.
   *
   * @param input {@code CharSequence} from which to extract the entities.
   */
  public void reset(CharSequence input) {
    Preconditions.checkNotNull(input);
    updateInputCharSequence(input);
    clearAttributes();

    if (triggeringChar > 0) {
      // triggeringChar is specified.
      boolean foundTriggeringChar = false;
      for (int i = 0; i < input.length(); i++) {
        if (triggeringChar == input.charAt(i)) {
          foundTriggeringChar = true;
          break;
        }
      }
      if (!foundTriggeringChar) {
        // No triggering char found. No extraction performed.
        matcher = null;
        return;
      }
    }

    if (regexPattern != null) {
      matcher = regexPattern.matcher(input);
    }
  }

  @Override
  public boolean incrementToken() {
    if (matcher != null && matcher.find()) {
      int start = matcher.start(startGroup);
      int end = matcher.end(endGroup);

      if (end > 0 && Character.isWhitespace(inputCharSequence().charAt(end - 1))) {
        end = end - 1;
      }
      updateOffsetAndLength(start, end - start);
      return true;
    } else {
      return false;
    }
  }

  public static class Builder extends AbstractBuilder<RegexExtractor, Builder> {
    public Builder() {
      super(new RegexExtractor());
    }
  }

  public abstract static class
      AbstractBuilder<N extends RegexExtractor, T extends AbstractBuilder<N, T>> {
    private final N extractor;

    protected AbstractBuilder(N transformer) {
      this.extractor = Preconditions.checkNotNull(transformer);
    }

    @SuppressWarnings("unchecked")
    protected T self() {
      return (T) this;
    }

    public T setRegexPattern(Pattern pattern) {
      Preconditions.checkNotNull(pattern);
      extractor.setRegexPattern(pattern);
      return self();
    }

    public T setRegexPattern(Pattern pattern, int startGroup, int endGroup) {
      Preconditions.checkNotNull(pattern);
      Preconditions.checkArgument(startGroup >= 0);
      Preconditions.checkArgument(endGroup >= 0);
      extractor.setRegexPattern(pattern, startGroup, endGroup);
      return self();
    }

    public T setTriggeringChar(char triggeringChar) {
      Preconditions.checkArgument(triggeringChar > 0);
      extractor.setTriggeringChar(triggeringChar);
      return self();
    }

    public N build() {
      return extractor;
    }
  }
}
