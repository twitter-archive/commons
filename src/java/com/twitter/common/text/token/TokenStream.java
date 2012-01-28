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

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.apache.lucene.util.AttributeSource;

import com.twitter.common.text.example.TokenizerUsageExample;
import com.twitter.common.text.token.attribute.CharSequenceTermAttribute;

/**
 * Abstraction to enumerate a sequence of tokens. This class represents the central abstraction in
 * Twitter's text processing library, and is similar to Lucene's TokenStream, with the following
 * exceptions:
 *
 * <ul>
 * <li>This class assumes that the input text is a {@link CharSequence}.
 * <li>Calls support chaining.
 * <li>Instances are reusable.
 * </ul>
 *
 * For an annotated example of how this class is used in practice, refer to
 * {@link TokenizerUsageExample}.
 */
public abstract class TokenStream extends AttributeSource {
  /**
   * Constructs a {@code TokenStream} using the default attribute factory.
   */
  public TokenStream() {
    super();
  }

  /**
   * Constructs a {@code TokenStream} using the supplied {@code AttributeFactory} for creating new
   * {@code Attribute} instances.
   *
   * @param factory attribute factory
   */
  protected TokenStream(AttributeSource.AttributeFactory factory) {
    super(factory);
  }

  /**
   * Constructs a {@code TokenStream} that uses the same attributes as the supplied one.
   *
   * @param input attribute source
   */
  protected TokenStream(AttributeSource input) {
    super(input);
  }

  /**
   * Consumers call this method to advance the stream to the next token.
   *
   * @return false for end of stream; true otherwise
   */
  public abstract boolean incrementToken();

  /**
   * Resets this {@code TokenStream} (and also downstream tokens if they exist) to parse a new
   * input.
   *
   * @param input new text to parse.
   */
  public abstract void reset(CharSequence input);

  /**
   * Converts this token stream into a list of {@code Strings}.
   *
   * @return the contents of the token stream as a list of {@code Strings}.
   */
  public List<String> toStringList() {
    List<String> tokens = Lists.newArrayList();

    if (hasAttribute(CharSequenceTermAttribute.class)) {
      CharSequenceTermAttribute termAttr = getAttribute(CharSequenceTermAttribute.class);

      while (incrementToken()) {
        tokens.add(termAttr.getTermString());
      }
    } else {
      throw new UnsupportedOperationException("This instance does not support toStringList()"
          + " because it does not support CharSequenceTermAttribute.");
    }

    return tokens;
  }

  /**
   * Searches and returns an instance of a specified class in this TokenStream chain.
   *
   * @param cls class to search for
   * @return instance of the class {@code cls} if found or {@code null} if not found
   */
  public <T extends TokenStream> T getInstanceOf(Class<T> cls) {
    Preconditions.checkNotNull(cls);
    if (cls.isInstance(this)) {
      return cls.cast(this);
    }
    return null;
  }
}
