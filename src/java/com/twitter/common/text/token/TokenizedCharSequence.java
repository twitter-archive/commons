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

import java.nio.CharBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import com.twitter.common.text.token.attribute.CharSequenceTermAttribute;
import com.twitter.common.text.token.attribute.PartOfSpeechAttribute;
import com.twitter.common.text.token.attribute.TokenGroupAttribute;
import com.twitter.common.text.token.attribute.TokenGroupAttributeImpl;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

/**
 * Keeps the original text as well as its tokenized tokens.
 */
public class TokenizedCharSequence implements CharSequence {
  public static final class Token implements CharSequence {
    public static final int DEFAULT_PART_OF_SPEECH = -1;

    private final CharBuffer term;
    private final TokenType type;
    private final int pos;
    private final int inc;
    private final TokenizedCharSequence group;

    protected Token(CharBuffer term, TokenType type) {
      this(term, type, DEFAULT_PART_OF_SPEECH);
    }

    protected Token(CharBuffer term, TokenType type, int pos) {
      this(term, type, pos, 1, null);
    }

    protected Token(CharBuffer term, TokenType type, int pos, int inc, @Nullable TokenizedCharSequence group) {
      this.term = term;
      this.type = type;
      this.pos = pos;
      this.inc = inc;
      this.group = group;
    }

    /**
     * Returns a new Token object which represents a term starting with {@code offset} and with
     * {@length}.
     *
     * @param offset offset of the sub-token
     * @param length length of the sub-token
     * @return a new Token object representing a sub-token
     */
    public Token tokenize(int offset, int length) {
      Preconditions.checkArgument(offset >= 0 && offset < getLength());
      Preconditions.checkArgument(length > 0 && length <= getLength());
      return new Token((CharBuffer)term.subSequence(offset, offset + length), type, pos);
    }

    @Override
    public String toString() {
      return term.toString();
    }

    @Override
    public int length() {
      return term.limit() - term.position();
    }

    @Override
    public char charAt(int index) {
      return term.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return term.subSequence(start, end);
    }

    public CharSequence getTerm() {
      return term;
    }

    public int getOffset() {
      return term.position();
    }

    public int getEndOffset() {
      return term.limit();
    }

    public int getLength() {
      return term.limit() - term.position();
    }

    public TokenType getType() {
      return type;
    }

    public int getPartOfSpeech() {
      return pos;
    }

    public int getPositionIncrement() {
      return inc;
    }

    public TokenizedCharSequence getGroup() {
      return group;
    }

    public boolean hasGroup() {
      return group != null;
    }
  }

  private final CharSequence term;
  private final List<Token> tokens;

  private List<String> strTokens = null;
  private Map<TokenType, List<Token>> typeToTokensMap = null;

  private String strValue = null;
  private int hashCode;
  private boolean hashCodeCalced = false;

  protected TokenizedCharSequence(CharSequence text, List<Token> tokens) {
    this.tokens = Collections.unmodifiableList(tokens);
    this.term = text;
  }

  @Override
  public char charAt(int index) {
    return term.charAt(index);
  }

  @Override
  public int length() {
    return term.length();
  }

  @Override
  public CharSequence subSequence(int fromIndex, int toIndex) {
    return term.subSequence(fromIndex, toIndex);
  }

  @Override
  public String toString() {
    if (strValue == null) {
      strValue = term.toString();
    }
    return strValue;
  }

  @Override
  public boolean equals(Object obj) {
    return (obj != null)
      && (obj instanceof TokenizedCharSequence)
      && ((TokenizedCharSequence) obj).term.toString().equals(this.term.toString());
  }

  @Override
  public int hashCode() {
    if (!hashCodeCalced) {
      hashCode = term.toString().hashCode();
      hashCodeCalced = true;
    }
    return hashCode;
  }

  /**
   * Returns all tokens.
   *
   * @return a list of tokens as CharBuffer objects
   */
  public List<Token> getTokens() {
    return tokens;
  }

  /**
   * Returns all tokens as String.
   *
   * @return a list of tokens as String objects
   */
  public List<String> getTokenStrings() {
    if (strTokens == null) {
      // lazy initialization
      strTokens = Lists.newArrayListWithCapacity(tokens.size());
      for (Token token : tokens) {
        strTokens.add(token.getTerm().toString());
      }
    }

    return strTokens;
  }

  /**
   * Returns tokens of one or more specified types.
   *
   * @param types token type(s)
   * @return tokens of the specified type(s)
   */
  public List<Token> getTokensOf(TokenType... types) {
    if (typeToTokensMap == null) {
      // lazy initialization
      typeToTokensMap = Maps.newHashMap();

      for (Token token : tokens) {
        List<Token> subtokens = typeToTokensMap.get(token.getType());
        if (subtokens == null) {
          subtokens = Lists.newArrayList(token);
          typeToTokensMap.put(token.getType(), subtokens);
        } else {
          subtokens.add(token);
        }
      }
    }

    if (types.length == 1) {
      return typeToTokensMap.get(types[0]);
    }

    List<Token> subtokens = Lists.newArrayList();
    for (TokenType type : types) {
      subtokens.addAll(typeToTokensMap.get(type));
    }
    return subtokens;
  }

  /**
   * Returns tokens of one or more specified types as Strings.
   *
   * @param types token type(s)
   * @return list of tokens of specified type(s) as String objects
   */
  public List<String> getTokenStringsOf(TokenType... types) {
    List<String> strSubtokens = Lists.newArrayListWithCapacity(tokens.size());
    for (Token token : getTokensOf(types)) {
      strSubtokens.add(token.getTerm().toString());
    }

    return strSubtokens;
  }

  public static final class Builder {
    private final CharSequence origText;
    private final List<Token> tokens;

    public Builder(CharSequence originalText) {
      Preconditions.checkNotNull(originalText);
      this.origText = originalText;
      tokens = Lists.newArrayList();
    }

    public Builder addToken(int offset, int length) {
      addToken(offset, length, TokenType.TOKEN);

      return this;
    }

    public Builder addToken(int offset, int length, TokenType type) {
      addToken(offset, length, type, PartOfSpeechAttribute.UNKNOWN);

      return this;
    }

    public Builder addToken(int offset, int length, TokenType type, int pos) {
      addToken(offset, length, type, pos, 1, null);

      return this;
    }

    public Builder addToken(
        int offset,
        int length,
        TokenType type,
        int pos,
        int inc,
        TokenizedCharSequence group) {
      Preconditions.checkArgument(offset >= 0);
      Preconditions.checkArgument(length >= 0);
      Preconditions.checkNotNull(type);
      Preconditions.checkArgument(inc >= 0);
      tokens.add(
          new Token(CharBuffer.wrap(origText, offset, offset + length), type, pos, inc, group));

      return this;
    }

    public TokenizedCharSequence build() {
      return new TokenizedCharSequence(origText, tokens);
    }
  }

  public static final TokenizedCharSequence createFrom(TokenStream tokenizer) {
    CharSequenceTermAttribute termAttr = tokenizer.getAttribute(CharSequenceTermAttribute.class);
    TokenTypeAttribute typeAttr = tokenizer.getAttribute(TokenTypeAttribute.class);
    PartOfSpeechAttribute posAttr = null;
    if (tokenizer.hasAttribute(PartOfSpeechAttribute.class)) {
      posAttr = tokenizer.getAttribute(PartOfSpeechAttribute.class);
    }
    PositionIncrementAttribute incAttr = null;
    if (tokenizer.hasAttribute(PositionIncrementAttribute.class)) {
      incAttr = tokenizer.getAttribute(PositionIncrementAttribute.class);
    }
    TokenGroupAttributeImpl groupAttr = null;
    if (tokenizer.hasAttribute(TokenGroupAttribute.class)) {
      groupAttr = (TokenGroupAttributeImpl) tokenizer.getAttribute(TokenGroupAttribute.class);
    }

    //Need to wait for increment token for termAttr to have charsequence properly set
    TokenizedCharSequence.Builder builder = null;

    while (tokenizer.incrementToken()) {
      if (builder == null) {
        //Now we can set the term sequence for the builder.
        builder = new TokenizedCharSequence.Builder(termAttr.getCharSequence());
      }
      builder.addToken(termAttr.getOffset(), termAttr.getLength(),
          typeAttr.getType(),
          posAttr == null ? Token.DEFAULT_PART_OF_SPEECH : posAttr.getPOS(),
          incAttr == null ? 1 : incAttr.getPositionIncrement(),
          groupAttr == null || groupAttr.isEmpty() ? null :
              (groupAttr.getSequence() == null ? createFrom(groupAttr.getTokenGroupStream()) :
                  groupAttr.getSequence())
      );
    }

    if (builder == null) { //Never entered tokenizer loop, build an empty string
      builder = new TokenizedCharSequence.Builder("");
    }

    return builder.build();
  }

  public static final TokenizedCharSequence createFrom(CharSequence text,
      TokenStream tokenizer) {
    tokenizer.reset(text);
    return createFrom(tokenizer);
  }

  public static final List<TokenizedCharSequence> createFromTokenGroupsIn(
      TokenStream stream) {
    TokenGroupAttribute groupAttr = stream.getAttribute(TokenGroupAttribute.class);

    List<TokenizedCharSequence> groups = Lists.newArrayList();
    while (stream.incrementToken()) {
      Builder builder = new Builder(stream.term());

      TokenStream groupStream = groupAttr.getTokenGroupStream();
      PartOfSpeechAttribute posAttr = null;
      if (groupStream.hasAttribute(PartOfSpeechAttribute.class)) {
        posAttr = groupStream.getAttribute(PartOfSpeechAttribute.class);
      }
      PositionIncrementAttribute incAttr = null;
      if (groupStream.hasAttribute(PositionIncrementAttribute.class)) {
        incAttr = groupStream.getAttribute(PositionIncrementAttribute.class);
      }

      TokenGroupAttributeImpl innerGroupAttr = null;
      if (groupStream.hasAttribute(TokenGroupAttribute.class)) {
        innerGroupAttr = (TokenGroupAttributeImpl) groupStream.getAttribute(TokenGroupAttribute.class);
      }

      while (groupStream.incrementToken()) {
        builder.addToken(groupStream.offset() - stream.offset(),
                         groupStream.length(),
                         groupStream.type(),
                         posAttr == null ? Token.DEFAULT_PART_OF_SPEECH : posAttr.getPOS(),
                         incAttr == null ? 1 : incAttr.getPositionIncrement(),
                         innerGroupAttr == null || innerGroupAttr.isEmpty() ? null :
                             (innerGroupAttr.getSequence() == null ? createFrom(innerGroupAttr.getTokenGroupStream()) :
                                 innerGroupAttr.getSequence()));
      }

      groups.add(builder.build());
    }

    return groups;
  }
}
