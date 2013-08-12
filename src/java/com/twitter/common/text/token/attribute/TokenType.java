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

/**
 * Contains all token types supported by {@code TokenTypeAttribute}.
 */
public enum TokenType {
  TOKEN("token"),
  PUNCTUATION("punctation"),
  HASHTAG("hashtag"),
  USERNAME("username"),
  EMOTICON("emoticon"),
  URL("URL"),
  STOCK("stock symbol"),
  CONTRACTION("contraction");

  // More human-readable version of the token type; avoid screaming.
  public final String name;
  private TokenType(String s) {
    this.name = s;
  }
}
