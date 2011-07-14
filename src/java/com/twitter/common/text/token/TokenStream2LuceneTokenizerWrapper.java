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

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Tokenizer;

import com.google.common.base.Preconditions;

/**
 * Converts a {@link TokenStream} instance to a Lucene's {@code Tokenizer} instance.
 */
public class TokenStream2LuceneTokenizerWrapper extends Tokenizer {
  private final TokenStream stream;

  public TokenStream2LuceneTokenizerWrapper(TokenStream stream, Reader input)
      throws IOException {
    super(stream.cloneAttributes());
    this.stream = stream;
    reset(input);
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (!stream.incrementToken()) {
      return false;
    }
    clearAttributes();
    restoreState(stream.captureState());
    return true;
  }

  @Override
  public void reset(Reader input) throws IOException {
    Preconditions.checkNotNull(input);
    StringWriter writer = new StringWriter();
    IOUtils.copy(input, writer);
    stream.reset(writer.toString());
  }
}
