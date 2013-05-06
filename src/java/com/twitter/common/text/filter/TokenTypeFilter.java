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

package com.twitter.common.text.filter;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

/**
 * Either filters out or retains tokens of a specified type(s). If the mode is {@code Mode.ACCEPT},
 * tokens are retained. If the mode is {@code Mode.REJECT}, tokens are filtered out.
 */
public class TokenTypeFilter extends TokenFilter {
  public enum Mode { ACCEPT, REJECT }

  private final TokenTypeAttribute typeAttr;

  private Set<TokenType> types = Sets.newHashSet();
  private Mode mode = Mode.ACCEPT;

  protected TokenTypeFilter(TokenStream inputStream) {
    super(inputStream);
    typeAttr = inputStream.getAttribute(TokenTypeAttribute.class);
  }

  protected void setTypesToFilter(TokenType... types) {
    Preconditions.checkNotNull(types);
    for (TokenType type : types) {
      Preconditions.checkNotNull(type);
      this.types.add(type);
    }
  }

  protected void setMode(Mode mode) {
    this.mode = mode;
  }

  @Override
  public boolean acceptToken() {
    boolean match = types.contains(typeAttr.getType());
    if (mode == Mode.REJECT) {
      match = !match;
    }

    return match;
  }

  public static final class Builder {
    private TokenTypeFilter filter;

    public Builder(TokenStream inputStream) {
      filter = new TokenTypeFilter(inputStream);
    }

    /**
     * Sets token types to accept or filter.
     *
     * @param types token types to accept or filter
     * @return this {@code Builder} object
     */
    public Builder setTypesToFilter(TokenType... types) {
      filter.setTypesToFilter(types);
      return this;
    }

    /**
     * Selects whether to accept tokens of the
     * specified types or reject them.
     *
     * @param mode {@code Mode.ACCEPT} or {@code Mode.REJECT}
     * @return this {@code Builder} object
     */
    public Builder setMode(Mode mode) {
      filter.setMode(mode);
      return this;
    }

    public TokenTypeFilter build() {
      return filter;
    }
  }
}
