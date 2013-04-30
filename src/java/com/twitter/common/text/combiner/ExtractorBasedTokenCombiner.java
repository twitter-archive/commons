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
import java.util.Queue;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.twitter.common.text.token.TokenProcessor;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.TokenType;

/**
 * Combines multiple tokens into a single one if they define an entity identified
 * by an extractor TokenStream.
 */
public class ExtractorBasedTokenCombiner extends TokenProcessor {
  private TokenStream extractor = null;
  private TokenType type = null;
  private Queue<State> nextStates = Lists.newLinkedList();

  // this map stores the start offsets and end offsets
  // of the tokens detected by extractor.
  private Map<Integer, Integer> offsetMap = Maps.newHashMap();

  public ExtractorBasedTokenCombiner(TokenStream inputStream) {
    super(inputStream);
  }

  protected void setExtractor(TokenStream extractor) {
    this.extractor = extractor;
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
      offsetMap.put(extractor.offset(), extractor.offset() + extractor.length());
    }
  }

  @Override
  public boolean incrementToken() {
    if (!nextStates.isEmpty()) {
      restoreState(nextStates.poll());
      return true;
    }

    if (!incrementInputStream()) {
      return false;
    }

    if (offsetMap.containsKey(offset())) {
      int startOffset = offset();
      int endOffset = offsetMap.get(startOffset);

      // if the current token matches the given pattern,
      // simply update its TypeAttribute.
      if (endOffset == startOffset + length()) {
        if (type != null) {
          updateType(type);
        }
        return true;
      }

      // store the attributes of the current token
      nextStates.add(captureState());

      while (incrementInputStream()) {
        // store the next token's status
        nextStates.add(captureState());

        int currentEndOffset = offset() + length();
        if (currentEndOffset == endOffset) {
          //found it!
          // restore attributes of the first token.
          restoreState(nextStates.poll());
          updateOffsetAndLength(startOffset, endOffset - startOffset);
          if (type != null) {
            updateType(type);
          }
          nextStates.clear();
          break;
        } else if (currentEndOffset > endOffset) {
          // cannot find it and currentEndOffset.
          // grows beyond expected. (tokenization mismatch??)
          break;
        }
      }

      if (!nextStates.isEmpty()) {
        restoreState(nextStates.poll());
      }
    }

    return true;
  }
}
