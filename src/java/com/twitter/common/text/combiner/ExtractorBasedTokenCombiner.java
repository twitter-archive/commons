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

package com.twitter.common.text.combiner;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import org.apache.lucene.util.AttributeSource;

import com.twitter.common.text.token.TokenProcessor;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.CharSequenceTermAttribute;
import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

/**
 * Combines multiple tokens into a single one if they define an entity identified
 * by an extractor TokenStream.
 */
public class ExtractorBasedTokenCombiner extends TokenProcessor {
  private final CharSequenceTermAttribute termAttr;
  private final CharSequenceTermAttribute inputTermAttr;
  private final TokenTypeAttribute typeAttr;

  private TokenStream extractor = null;
  private CharSequenceTermAttribute extractorTermAttr;
  private TokenType type = null;

  private AttributeSource.State state;

  // this map stores the start offsets and end offsets
  // of the tokens detected by extractor.
  private Map<Integer, Integer> offsetMap = Maps.newHashMap();

  public ExtractorBasedTokenCombiner(TokenStream inputStream) {
    super(inputStream);
    Preconditions.checkArgument(hasAttribute(CharSequenceTermAttribute.class));
    termAttr = getAttribute(CharSequenceTermAttribute.class);
    typeAttr = addAttribute(TokenTypeAttribute.class);
    inputTermAttr = inputStream.getAttribute(CharSequenceTermAttribute.class);
  }

  protected void setExtractor(TokenStream extractor) {
    this.extractor = extractor;
    extractorTermAttr = extractor.getAttribute(CharSequenceTermAttribute.class);
  }

  protected void setType(TokenType type) {
    this.type = type;
  }

  @Override
  public void reset(CharSequence input) {
    super.reset(input);

    Preconditions.checkNotNull(extractor);

    offsetMap.clear();
    extractor.reset(input);
    while (extractor.incrementToken()) {
      offsetMap.put(extractorTermAttr.getOffset(),
          extractorTermAttr.getOffset() + extractorTermAttr.getLength());
    }
  }

  @Override
  public boolean incrementToken() {
    clearAttributes();
    if (state != null) {
      restoreState(state);
      state = null;
    } else {
      if (!getInputStream().incrementToken()) {
        return false;
      }

      restoreState(getInputStream().captureState());
    }

    if (offsetMap.containsKey(termAttr.getOffset())) {
      int startOffset = termAttr.getOffset();
      int endOffset = offsetMap.get(startOffset);

      // if the current token matches the given pattern,
      // simply update its TypeAttribute.
      if (endOffset == inputTermAttr.getOffset() + inputTermAttr.getLength()) {
        if (type != null) {
          typeAttr.setType(type);
        }
        return true;
      }

      while (getInputStream().incrementToken()) {
        state = getInputStream().captureState();

        int currentEndOffset = inputTermAttr.getOffset() + inputTermAttr.getLength();
        if (currentEndOffset == endOffset) {
          //found it!
          termAttr.setLength(endOffset - startOffset);
          if (type != null) {
            typeAttr.setType(type);
          }
          state = null;

          break;
        } else if (currentEndOffset > endOffset) {
          // cannot find it and currentEndOffset
          // grows beyond expected (tokenization mismatch??)
          break;
        }
      }
    }

    return true;
  }
}
