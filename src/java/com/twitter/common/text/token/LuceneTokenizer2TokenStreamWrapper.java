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
import java.io.StringReader;

import com.google.common.base.Preconditions;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/**
 * Converts a Lucene {@link Tokenizer} instance into {@link TokenStream} instance.
 */
public class LuceneTokenizer2TokenStreamWrapper extends TokenStream {
  private final Tokenizer tokenizer;
  private final OffsetAttribute inputOffsetAttr;

  public LuceneTokenizer2TokenStreamWrapper(Tokenizer tokenizer) {
    super(tokenizer.cloneAttributes());
    this.tokenizer = tokenizer;
    inputOffsetAttr = tokenizer.getAttribute(OffsetAttribute.class);
  }

  @Override
  public boolean incrementToken() {
    clearAttributes();

    try {
      if (!tokenizer.incrementToken()) {
        return false;
      }

      restoreState(tokenizer.captureState());

      updateOffsetAndLength(inputOffsetAttr.startOffset(),
                            inputOffsetAttr.endOffset() - inputOffsetAttr.startOffset());

      return true;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void reset(CharSequence input) {
    Preconditions.checkNotNull(input);
    try {
      tokenizer.setReader(new StringReader(input.toString()));
      updateInputCharSequence(input);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
