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

import java.nio.CharBuffer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.apache.lucene.util.AttributeSource;

import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.TokenType;

/**
 * Tokenizes text based on regular expressions of word delimiters and punctuation characters.
 */
public class RegexTokenizer extends TokenStream {
  private Pattern delimiterPattern;
  private int punctuationGroup = 0;
  private boolean keepPunctuation = false;

  private List<CharBuffer> tokens;
  private List<TokenType> tokenTypes;
  private int tokenIndex = 0;

  // please use Builder instead.
  protected RegexTokenizer() {
  }

  protected RegexTokenizer(AttributeSource attributeSource) {
    super(attributeSource);
  }

  protected void setDelimiterPattern(Pattern delimiterPattern) {
    this.delimiterPattern = delimiterPattern;
  }

  protected void setPunctuationGroupInDelimiterPattern(int group) {
    this.punctuationGroup = group;
  }

  protected void setKeepPunctuation(boolean keepPunctuation) {
    this.keepPunctuation = keepPunctuation;
  }

  @Override
  public boolean incrementToken() {
    if (tokenIndex >= tokens.size()) {
      return false;
    }

    CharBuffer token = tokens.get(tokenIndex);

    updateOffsetAndLength(token.position(), token.limit() - token.position());
    updateType(tokenTypes.get(tokenIndex));

    tokenIndex++;

    return true;
  }

  @Override
  public void reset(CharSequence input) {
    // reset termAttr
    updateInputCharSequence(input);
    clearAttributes();

    // reset tokens
    tokens = Lists.newArrayList();
    tokenTypes = Lists.newArrayList();

    Matcher matcher = delimiterPattern.matcher(input);
    int lastMatch = 0;

    while (matcher.find()) {
      if (matcher.start() != lastMatch) {
        tokens.add(CharBuffer.wrap(input, lastMatch, matcher.start()));
        tokenTypes.add(TokenType.TOKEN);
      }

      if (keepPunctuation && matcher.start(punctuationGroup) >= 0) {
        tokens.add(CharBuffer.wrap(input, matcher.start(punctuationGroup),
            matcher.end(punctuationGroup)));
        tokenTypes.add(TokenType.PUNCTUATION);
      }

      lastMatch = matcher.end();
    }
    if (lastMatch < input.length()) {
      tokens.add(CharBuffer.wrap(input, lastMatch, input.length()));
      tokenTypes.add(TokenType.TOKEN);
    }

    // reset tokenIndex
    tokenIndex = 0;
  }

  /**
   * Builder for RegexTokenizer.
   *
   * @author Keita Fujii
   */
  public static final class Builder extends AbstractBuilder<RegexTokenizer, Builder> {
    @Override
    protected RegexTokenizer buildTokenizer(@Nullable AttributeSource attributeSource) {
      if (attributeSource == null) {
        return new RegexTokenizer();
      } else {
        return new RegexTokenizer(attributeSource);
      }
    }
  }

  public abstract static class
      AbstractBuilder<N extends RegexTokenizer, T extends AbstractBuilder<N, T>> {
    private Pattern delimiterPattern;
    private int punctuationGroup = 0;
    private boolean keepPunctuation = false;

    @SuppressWarnings("unchecked")
    protected T self() {
      return (T) this;
    }

    /**
     * Sets the Regex pattern of the delimiter.
     *
     * An input text is tokenized by the CharSequence
     * specified by this pattern.
     *
     * @param delimiterPattern Regex pattern of delimiter.
     * @return this Builder object
     */
    public T setDelimiterPattern(Pattern delimiterPattern) {
      this.delimiterPattern = delimiterPattern;
      return self();
    }

    /**
     * Sets the ID of the group in delimiterPattern that should
     * be handled as punctuation.
     * For example, you can set delimiterPattern as "([.,])\\s+"
     * and punctuationGroup as 1 in order to detect comma
     * and period as punctuations.
     *
     * @param group group ID of punctuation in delimiterPattern.
     * @return this Builder object
     */
    public T setPunctuationGroupInDelimiterPattern(int group) {
      this.punctuationGroup = group;
      return self();
    }

    /**
     * Specifies whether to keep punctuations (which is specified
     * by delimiterPattern and punctuationGroupInDelimiterPattern)
     * in the output token stream.
     *
     * @param keepPunctuation true to keep delimiters. false otherwise.
     * @return this Builder object.
     */
    public T setKeepPunctuation(boolean keepPunctuation) {
      this.keepPunctuation = keepPunctuation;
      return self();
    }

    protected abstract N buildTokenizer(@Nullable AttributeSource attributeSource);

    private void initialize(N tokenizer) {
      Preconditions.checkNotNull(delimiterPattern);
      Preconditions.checkArgument(punctuationGroup >= 0);

      tokenizer.setDelimiterPattern(delimiterPattern);
      tokenizer.setPunctuationGroupInDelimiterPattern(punctuationGroup);
      tokenizer.setKeepPunctuation(keepPunctuation);
    }

    public N build() {
      N tokenizer = buildTokenizer(null);
      initialize(tokenizer);

      return tokenizer;
    }

    public N build(AttributeSource attributeSource) {
      N tokenizer = buildTokenizer(attributeSource);
      initialize(tokenizer);

      return tokenizer;
    }
  }
}
