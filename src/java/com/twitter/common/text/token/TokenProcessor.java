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

/**
 * A {@code TokenStream} whose input is another {@code TokenStream}.
 * In other words, this class corresponds to TokenFilter in Lucene.
 */
public abstract class TokenProcessor extends TokenStream {
  private final TokenStream inputStream;

  /**
   * Constructs a new {@code TokenProcessor}.
   *
   * @param inputStream input {@code TokenStream}
   */
  public TokenProcessor(TokenStream inputStream) {
    // This clones all attributes of the input stream to this one.
    super(inputStream.cloneAttributes());
    this.inputStream = inputStream;
  }

  @Override
  public void reset(CharSequence input) {
    clearAttributes();
    inputStream.reset(input);
  }

  protected TokenStream getInputStream() {
    return inputStream;
  }

  @Override
  public <T extends TokenStream> T getInstanceOf(Class<T> cls) {
    if (cls.isInstance(this)) {
      return cls.cast(this);
    }
    return inputStream.getInstanceOf(cls);
  }
}
