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

package com.twitter.common.text.token.attribute;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectStreamException;

import org.apache.lucene.util.AttributeImpl;

/**
 * Implementation of {@link TokenTypeAttribute}.
 * <p>
 * Note that this class explicitly suppresses the ability for instance to be serialized, inherited
 * via {@link AttributeImpl}.
 */
@SuppressWarnings("serial")
public class TokenTypeAttributeImpl extends AttributeImpl implements TokenTypeAttribute {
  private TokenType type = TokenType.TOKEN;

  @Override
  public void setType(TokenType type) {
    this.type = type;
  }

  @Override
  public TokenType getType() {
    return type;
  }

  @Override
  public void clear() {
    type = TokenType.TOKEN;
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    return other != null
           && other instanceof TokenTypeAttributeImpl
           && ((TokenTypeAttributeImpl) other).type == this.type;
  }

  @Override
  public void copyTo(AttributeImpl target) {
    if (target instanceof TokenTypeAttributeImpl) {
      ((TokenTypeAttributeImpl) target).setType(getType());
    }
  }

  // Explicitly suppress ability to serialize.
  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    throw new NotSerializableException();
  }

  private void readObject(java.io.ObjectInputStream in)
      throws IOException, ClassNotFoundException {
    throw new NotSerializableException();
  }

  private void readObjectNoData() throws ObjectStreamException {
    throw new NotSerializableException();
  }
}
