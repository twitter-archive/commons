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

/**
 * Attribute representing the part of speech of a token. When a tokenizer segments text into a
 * series of token, it may provide the part of speech (e.g., Noun, Verb) of each token.
 * This interface can be used to expose POS information.
 */
public interface PartOfSpeechAttribute extends Attribute {
  int UNKNOWN = -1;

  /**
   * Set PartOfSpeech (POS) of this token.
   * @param pos POS of this token.
   */
  void setPOS(int pos);

  /**
   * Return PartOfSpeech (POS) of this token.
   * @return POS of this token.
   */
  int getPOS();
}
