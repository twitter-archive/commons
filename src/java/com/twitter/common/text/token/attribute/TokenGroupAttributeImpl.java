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
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeSource;

import com.twitter.common.text.token.TokenGroupStream;

/**
 * Implementation of {@link TokenGroupAttribute}.
 * <p>
 * Note that this class explicitly suppresses the ability for instance to be serialized, inherited
 * via {@link AttributeImpl}.
 */
public class TokenGroupAttributeImpl extends AttributeImpl implements TokenGroupAttribute {
  private static final long serialVersionUID = 0L;

  private List<AttributeSource.State> states = Collections.emptyList();
  private AttributeSource attributeSource;

  @Override
  public void clear() {
    states = Collections.emptyList();
  }

  @Override
  public void copyTo(AttributeImpl obj) {
    if (obj instanceof TokenGroupAttributeImpl) {
      TokenGroupAttributeImpl attr = (TokenGroupAttributeImpl) obj;
      attr.setAttributeSource(attributeSource);
      attr.setStates(Lists.newArrayList(states));
    }
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof TokenGroupAttributeImpl)
      && ((TokenGroupAttributeImpl) obj).states.equals(states);
  }

  @Override
  public int hashCode() {
    return states.hashCode();
  }

  @Override
  public boolean isEmpty() {
    return states.isEmpty();
  }

  @Override
  public int size() {
    return states.size();
  }

  public void setStates(List<AttributeSource.State> states) {
    this.states = states;
  }

  public void setAttributeSource(AttributeSource source) {
    this.attributeSource = source;
  }

  @Override
  public TokenGroupStream getTokenGroupStream() {
    return new TokenGroupStream(attributeSource, states);
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
