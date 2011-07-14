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

import org.apache.lucene.analysis.tokenattributes.TermAttribute;

import com.twitter.common.text.token.TokenProcessor;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.CharSequenceTermAttribute;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

/**
 * Updates {@code TypeAttribute} of a token if the term matches a given regular expression.
 */
public class RegexDetector extends TokenProcessor {
  private CharSequenceTermAttribute inputCharSeqTermAttr;
  private TermAttribute inputTermAttr;
  private TokenTypeAttribute typeAttr;

  private Pattern regexPattern;
  private TokenType type;

  protected RegexDetector(TokenStream inputStream) {
    super(inputStream);
    if (inputStream.hasAttribute(CharSequenceTermAttribute.class)) {
      inputCharSeqTermAttr = inputStream.getAttribute(CharSequenceTermAttribute.class);
      inputTermAttr = null;
    } else {
      inputTermAttr = inputStream.getAttribute(TermAttribute.class);
      inputCharSeqTermAttr = null;
    }
    typeAttr = addAttribute(TokenTypeAttribute.class);
  }

  protected void setRegexPattern(Pattern regex) {
    this.regexPattern = regex;
  }

  protected void setType(TokenType type) {
    this.type = type;
  }

  @Override
  public boolean incrementToken() {
    TokenStream inputStream = getInputStream();

    if (!inputStream.incrementToken()) {
      return false;
    }
    clearAttributes();
    restoreState(inputStream.captureState());

    CharSequence term;
    if (inputCharSeqTermAttr != null) {
      term = inputCharSeqTermAttr.getTermCharSequence();
    } else {
      term = inputTermAttr.term();
    }
    if (regexPattern.matcher(term).matches()) {
      typeAttr.setType(type);
    }

    return true;
  }

  public static class Builder extends AbstractBuilder<RegexDetector, Builder> {
    public Builder(TokenStream inputStream) {
      super(new RegexDetector(inputStream));
    }
  }

  public abstract static class
      AbstractBuilder<N extends RegexDetector, T extends AbstractBuilder<N, T>> {
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

    public T setRegexPattern(Pattern regex) {
      Preconditions.checkNotNull(regex);
      detector.setRegexPattern(regex);
      return self();
    }

    public T setType(TokenType type) {
      Preconditions.checkNotNull(type);
      detector.setType(type);
      return self();
    }

    public N build() {
      return detector;
    }
  }
}
