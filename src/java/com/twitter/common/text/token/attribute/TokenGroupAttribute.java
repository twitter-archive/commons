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
import org.apache.lucene.util.AttributeSource;

import com.twitter.common.text.token.TokenGroupStream;

/**
 * Attribute representing a group of tokens.
 */
public interface TokenGroupAttribute extends Attribute {
  /**
   * Returns true if this is an empty group.
   *
   * @return true if this is an empty group.
   */
  boolean isEmpty();

  /**
   * Returns the size of this {@code TokenGroup}.
   *
   * @return size of this {@code TokenGroup}
   */
  int size();

  /**
   * Returns a {@code TokenGroupStream}, which provides access to individual tokens in this group.
   * Use {@link TokenGroupStream#incrementToken()} to iterate over the member Tokens in this group,
   * and {@link AttributeSource#getAttribute(Class)} to obtain the attribute(s) of each token.
   *
   * @return {@code TokenGroupStream} to access to the members of this group.
   */
  TokenGroupStream getTokenGroupStream();
}
