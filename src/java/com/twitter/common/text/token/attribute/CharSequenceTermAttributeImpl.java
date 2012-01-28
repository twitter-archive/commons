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
import java.io.Serializable;
import java.nio.CharBuffer;

import com.google.common.base.Preconditions;

import org.apache.lucene.util.AttributeImpl;

/**
 * Implementation of {@code CharSequenceTermAttribute}. The implementation differs from Lucene's
 * {@code TermAttributeImpl}, which relies on an internal char[] termBuffer that can grow.
 * Extracting a token with {@code TermAttributeImpl} involves a copy into this buffer, and setting
 * the length of the term. In contrast, with this class, the client instead refers to
 * a span in the underlying {@code CharSequence} by start index (offset) and end index.
 * <p>
 * Note that this class explicitly suppresses the ability for instance to be serialized, inherited
 * via {@link AttributeImpl}.
 */
public class CharSequenceTermAttributeImpl extends AttributeImpl
  implements CharSequenceTermAttribute, Cloneable, Serializable {
  private static final long serialVersionUID = 0L;

  private CharSequence charSequence = "";
  private int offset = 0;
  private int length = 0;
  private int hashCode = 0;

  @Override
  public CharSequence getTermCharSequence() {
    // CharBuffer.wrap for CharSequences takes start and end indices.
    return CharBuffer.wrap(charSequence, offset, offset + length);
  }

  @Override
  public String getTermString() {
    return charSequence.subSequence(offset, offset + length).toString();
  }

  @Override
  public void setTermBuffer(CharSequence seq) {
    Preconditions.checkNotNull(seq);
    charSequence = seq;
    setOffset(0);
    setLength(seq.length());
  }

  @Override
  public void setTermBuffer(CharSequence seq, int offset, int length) {
    charSequence = seq;
    setOffset(offset);
    setLength(length);
  }

  @Override
  public void clear() {
    setOffset(0);
    setLength(0);
  }

  /**
   * Passing a {@code CharSequenceTermAttribute} instead of a {@code TermAttribute} will
   * obviate the construction of an extra String.
   */
  @Override
  public void copyTo(AttributeImpl target) {
    if (target instanceof CharSequenceTermAttribute) {
      CharSequenceTermAttribute attr = (CharSequenceTermAttribute) target;
      attr.setTermBuffer(charSequence, offset, length);
    }
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (other instanceof CharSequenceTermAttribute) {
      CharSequenceTermAttributeImpl otherImpl = (CharSequenceTermAttributeImpl) other;

      if (otherImpl.charSequence == charSequence
          && otherImpl.length == length && otherImpl.offset == offset) {
        return true;
      }

      if (otherImpl.length != length) {
        return false;
      }

      for (int i = 0; i < otherImpl.length; i++) {
        if (otherImpl.charSequence.charAt(otherImpl.offset + i)
            != charSequence.charAt(offset + i)) {
          return false;
        }
      }

      return true;
    }

    return false;
  }

  /**
   * This is largely based on {@link org.apache.lucene.util.ArrayUtil#hashCode(char[], int, int)}.
   */
  @Override
  public int hashCode() {
    if (hashCode == 0) {
      for (int i = offset; i < offset + length; i++) {
        hashCode = hashCode * 31 + charSequence.charAt(i);
      }
    }
    return hashCode;
  }

  @Override
  public int getOffset() {
    return offset;
  }

  @Override
  public int getLength() {
    return length;
  }

  @Override
  public void setOffset(int offset) {
    if (offset < 0 || offset > charSequence.length()) {
      throw new IndexOutOfBoundsException("Offset " + offset + " must be >= 0 and < "
          + charSequence.length() + ", which is the length of the underlying CharSequence.");
    }
    this.offset = offset;
    this.hashCode = 0;
  }

  @Override
  public void setLength(int length) {
    if (length < 0 || length > charSequence.length()) {
      throw new IndexOutOfBoundsException("Length " + length + " must be >= 0 and <= "
          + charSequence.length() + ", which is the length of the underlying CharSequence.");
    }
    this.length = length;
    this.hashCode = 0;
  }

  @Override
  public CharSequence getCharSequence() {
    return charSequence;
  }

  @Override
  public void setCharSequence(CharSequence originalCharSequence) {
    charSequence = originalCharSequence;
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
