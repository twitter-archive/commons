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

import com.google.common.base.Preconditions;

/**
 * A {@code TokenStream} whose input is another {@code TokenStream}.
 * In other words, this class corresponds to TokenFilter in Lucene.
 */
public abstract class TokenProcessor extends TokenStream {
  private final TokenStream inputStream;

  private final TokenProcessor inputProcessor;

  private boolean enabled = true;

  /**
   * Constructs a new {@code TokenProcessor}.
   *
   * @param inputStream input {@code TokenStream}
   */
  public TokenProcessor(TokenStream inputStream) {
    super(Preconditions.checkNotNull(inputStream));
    this.inputStream = inputStream;

    // let's check if the inputStream is TokenProcessor or not in the constructor
    // so that you don't have to call instanceof again
    this.inputProcessor = (inputStream instanceof TokenProcessor) ? (TokenProcessor) inputStream : null;
  }

  @Override
  public void reset(CharSequence input) {
    getNextEnabledInputStream().reset(input);
  }

  /**
   * Increment the underlying input stream.
   * @return true if the input stream has more token. False otherwise.
   */
  protected boolean incrementInputStream() {
    return getNextEnabledInputStream().incrementToken();
  }

  /**
   * Enable this {@code TokenProcessor}
   */
  public final void enable() {
    this.enabled = true;
  }

  /**
   * Disable this {@code TokenProcessor}
   */
  public final void disable() {
    this.enabled = false;
  }

  /**
   * Return true if this {@code TokenProcessor} is enabled. False otherwise
   * @return true if this is enabled.
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Enable or disable this {@code TokenProcessor}
   * @param enabled true to enable this. false to disable.
   */
  public final void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  protected TokenStream getNextEnabledInputStream() {
    if (inputProcessor == null) {
      return inputStream;
    } else if (inputProcessor.isEnabled()) {
      return inputProcessor;
    } else {
      return inputProcessor.getNextEnabledInputStream();
    }
  }

  @Override
  public <T extends TokenStream> T getInstanceOf(Class<T> cls) {
    if (cls.isInstance(this)) {
      return cls.cast(this);
    }
    return inputStream.getInstanceOf(cls);
  }

}
