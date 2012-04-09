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

package com.twitter.common.text.util;

import java.io.IOException;

import org.apache.lucene.util.AttributeSource;

import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

/**
 * (De)Serializes {@link TokenTypeAttribute)s.
 */
public class TokenTypeAttributeSerializer implements TokenStreamSerializer.AttributeSerializer {
  private TokenTypeAttribute tokenTypeAttribute;

  @Override
  public void initialize(AttributeSource attributeSource, TokenStreamSerializer.Version version)
      throws IOException {
    this.tokenTypeAttribute = attributeSource.addAttribute(TokenTypeAttribute.class);
  }

  @Override
  public void serialize(TokenStreamSerializer.AttributeOutputStream output) throws IOException {
    output.writeByte(tokenTypeAttribute.getType().ordinal());
  }

  @Override
  public void deserialize(TokenStreamSerializer.AttributeInputStream input,
                             CharSequence charSequence) throws IOException {
    tokenTypeAttribute.setType(TokenType.values()[input.readByte()]);
  }
}
