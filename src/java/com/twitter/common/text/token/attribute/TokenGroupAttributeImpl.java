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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.apache.lucene.util.Attribute;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.AttributeSource.State;

import com.twitter.common.text.token.TokenGroupStream;
import com.twitter.common.text.token.TokenizedCharSequence;
import com.twitter.common.text.token.TokenizedCharSequenceStream;

/**
 * Implementation of {@link TokenGroupAttribute}.
 * <p>
 * Note that this class explicitly suppresses the ability for instance to be serialized, inherited
 * via {@link AttributeImpl}.
 */
public class TokenGroupAttributeImpl extends AttributeImpl implements TokenGroupAttribute {
  private static final long serialVersionUID = 0L;

  private ImmutableList<Class<? extends Attribute>> attributeClasses;
  private List<State> states = Collections.emptyList();
  private TokenizedCharSequence seq = null;

  // this is lazy-initialized and should not be cloned.
  private TokenGroupStream tokenGroupStream = null;

  @Override
  public void clear() {
    states = Collections.emptyList();
    seq = null;
    tokenGroupStream = null;
  }

  @Override
  public void copyTo(AttributeImpl obj) {
    if (obj instanceof TokenGroupAttributeImpl) {
      TokenGroupAttributeImpl attr = (TokenGroupAttributeImpl) obj;
      attr.attributeClasses = this.attributeClasses;
      attr.states = this.states;
      attr.seq = this.seq;
      attr.tokenGroupStream = null;
    }
  }

  @Override
  public AttributeImpl clone() {
    TokenGroupAttributeImpl clone = new TokenGroupAttributeImpl();
    // we don't need to clone attributeClasses because it's immutable.
    clone.attributeClasses = attributeClasses;
    // same here. TokenizedCharSequence is an immutable obj so no need to clone.
    clone.seq = seq;
    ImmutableList.Builder<State> builder = ImmutableList.builder();
    for (State state : states) {
      builder.add(state.clone());
    }
    clone.states = builder.build();
    clone.tokenGroupStream = null;
    return clone;
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof TokenGroupAttributeImpl)
      && (((TokenGroupAttributeImpl) obj).states.equals(states) &&
          ((TokenGroupAttributeImpl) obj).seq == null && seq == null) ||
         (((TokenGroupAttributeImpl) obj).seq != null && seq != null &&
            ((TokenGroupAttributeImpl) obj).seq.equals(seq));
  }

  @Override
  public int hashCode() {
    return (seq == null ? states.hashCode() : seq.hashCode());
  }

  @Override
  public boolean isEmpty() {
    return states.isEmpty() && (seq == null || seq.getTokens().isEmpty());
  }

  @Override
  public int size() {
    return (!states.isEmpty() ? states.size() :
        (seq != null ? seq.getTokens().size() : states.size()));
  }

  /**
   * Sets the list of states for this group. Invalidates any previously set sequence.
   */
  public void setStates(List<AttributeSource.State> states) {
    // A State contains clones of AttributeImpl, so we must make sure that
    // no AttributeImpl holds a circular reference back to itself.
    this.states = ImmutableList.copyOf(states);
    this.seq = null;
  }

  /**
   * Sets the attribute source for this group. Invalidates any previously set sequence.
   */
  public void setAttributeSource(AttributeSource source) {
    attributeClasses = ImmutableList.copyOf(source.getAttributeClassesIterator());
    this.seq = null;
  }

  /**
   * Sets the group token stream as a sequence. Constructs a stream from this sequence lazily.
   * Invalidates any information set from setStates or setAttributeSource
   */
  public void setSequence(TokenizedCharSequence seq) {
    this.seq = seq;
    this.states = Collections.emptyList();
    this.attributeClasses = null;
  }

  /**
   * Returns the backing TokenizedCharSequence. Will be null if group was set using states
   */
  public TokenizedCharSequence getSequence() {
    return seq;
  }

  @Override
  public TokenGroupStream getTokenGroupStream() {
    //Lazily process the sequence into a set of states, only do it when getTokenGroupStream is called
    if ((attributeClasses == null || states.isEmpty()) && seq != null) {
      TokenizedCharSequenceStream ret = new TokenizedCharSequenceStream();
      ret.reset(seq);

      //TODO(alewis) This could probably be lazier. Make a new extension of TokenGroupStream?
      ImmutableList.Builder<State> builder = ImmutableList.builder();
      while (ret.incrementToken()) {
        builder.add(ret.captureState());
      }
      setAttributeSource(ret);
      setStates(builder.build());
    }
    // lazy initialize tokenGroupStream
    if (tokenGroupStream == null) {
      tokenGroupStream = new TokenGroupStream(attributeClasses);
    }
    tokenGroupStream.setStates(states);
    return tokenGroupStream;
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
