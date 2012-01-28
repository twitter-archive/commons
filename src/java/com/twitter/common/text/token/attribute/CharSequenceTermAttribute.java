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

import org.apache.lucene.util.Attribute;

import com.twitter.common.text.token.TokenStream;

/**
 * {@code TermAttribute} backed by a larger {@link CharSequence} that does not change as
 * {@link TokenStream#incrementToken()} is called. Instead, the offset and character length are
 * updated to reference a new span with respect to the underlying {@code CharSequence}.
 */
public interface CharSequenceTermAttribute extends Attribute {
  /**
   * The offset is the character index, with respect to the underlying CharSequence,
   * of the first character in the span referenced by this CharSequenceTermAttribute.
   * The offset may point to the end of the underlying CharSequence when length is zero.
   *
   * @return the current offset
   */
  int getOffset();

  /**
   * The length is the length in characters of the span referenced by this
   * {@link CharSequenceTermAttribute}.
   *
   * @return the current length
   */
  int getLength();

  /**
   * Assigns the offset to the specified value.
   *
   * @param offset new value for the offset, which must be at least zero, and less
   *               than or equal to the length of the underlying {@code CharSequence}
   * @throws IndexOutOfBoundsException if the specified offset is out of bounds
   */
  void setOffset(int offset);

  /**
   * Assigns the length to the specified value.
   *
   * @param length new value for the length, which must be at least zero, and at most
   *               equal to the length of the underlying {@code CharSequence}
   * @throws IndexOutOfBoundsException if the specified length is out of bounds
   */
  void setLength(int length);

  /**
   * Sets the encapsulated {@code CharSequence}.
   *
   * @param originalCharSequence {@code CharSequence} encapsulated by this
   *     {@code CharSequenceAttribute}
   */
  void setCharSequence(CharSequence originalCharSequence);

  /**
   * Provides access to the encapsulated {@code CharSequence}.
   *
   * @return the underlying {@code CharSequence} object
   */
  CharSequence getCharSequence();

  /**
   * Assigns the backing {@code CharSequence} for this attribute to the specified {@code
   * CharSequence}. The start character index is set to zero, and the end character index is set to
   * the length of the specified {@code CharSequence}.
   *
   * @param seq {@code CharSequence} that will become the new underlying {@code CharSequence} for
   *          this attribute.
   */
  void setTermBuffer(CharSequence seq);

  /**
   * Assigns the backing {@code CharSequence} for this attribute to the specified {@code
   * CharSequence}. The start character index is set to specified offset, and the end character
   * index is set to offset plus length.
   *
   * @param seq {@code CharSequence} that will become the new underlying {@code CharSequence} for
   *          this attribute.
   * @param offset character index with respect to the specified {@code CharSequence} that will
   *          become the new start character index for this attribute.
   * @param length this value will be added to the specified offset value, and the result will
   *          become the new end character index for this attribute.
   */
  void setTermBuffer(CharSequence seq, int offset, int length);


  /**
   * Returns the term text as a {@code CharSequence}, without needing to construct a
   * {@code String}. This method is preferred over {@link getTermString()}.
   *
   * @return {@code CharSequence} representing the term text.
   */
  CharSequence getTermCharSequence();

  /**
   * Returns the term text as a {@code String}.
   * {@link getTermCharSequence()} is preferred over this method.
   *
   * @return {@code String} representing the term text.
   */
  String getTermString();
}
