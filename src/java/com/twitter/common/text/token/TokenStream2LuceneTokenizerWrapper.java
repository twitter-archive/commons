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
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import com.google.common.base.Preconditions;

/**
 * Converts a {@link TokenStream} instance to a Lucene's {@code Tokenizer} instance.
 */
public class TokenStream2LuceneTokenizerWrapper extends Tokenizer {
  private final CharTermAttribute charTermAttr = addAttribute(CharTermAttribute.class);
  private final OffsetAttribute offsetAttr = addAttribute(OffsetAttribute.class);

  private final TokenStream inputStream;

  public TokenStream2LuceneTokenizerWrapper(TokenStream inputStream, Reader input)
      throws IOException {
    super(inputStream, input);
    this.inputStream = Preconditions.checkNotNull(inputStream);
    addAttribute(PositionIncrementAttribute.class);
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (!inputStream.incrementToken()) {
      return false;
    }

    // copy the attributes of input stream.
    charTermAttr.setEmpty();
    charTermAttr.append(inputStream.term());
    offsetAttr.setOffset(inputStream.offset(), inputStream.offset() + inputStream.length());

    return true;
  }

  /**
   * Make sure we copy the reader's content to the TokenStream.
   */
  public void reset() throws IOException {
    clearAttributes();
    inputStream.reset(IOUtils.toString(input));
  }
}
