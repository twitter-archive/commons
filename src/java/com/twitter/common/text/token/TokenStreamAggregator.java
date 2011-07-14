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

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import org.apache.lucene.util.Attribute;

/**
 * Aggregates the outputs of multiple {@code TokenStreams} into a single {@code TokenStream}.
 */
public class TokenStreamAggregator extends TokenStream {
  private final List<TokenStream> aggregatedStreams;

  protected TokenStreamAggregator(TokenStream... streams) {
    aggregatedStreams = ImmutableList.copyOf(streams);

    // register all attributes of every stream
    for (TokenStream stream : streams) {
      Iterator<Class<? extends Attribute>> it = stream.getAttributeClassesIterator();
      while (it.hasNext()) {
        addAttribute(it.next());
      }
    }
  }

  /**
   * Creates a {@code TokenStream} that aggregates the outputs of a given set of
   * {@code TokenStreams}.
   *
   * @param streams TokenStreams to aggregate
   * @return an aggregated TokenStream
   */
  public static final TokenStream of(TokenStream... streams) {
    return new TokenStreamAggregator(streams);
  }

  @Override
  public boolean incrementToken() {
    for (TokenStream stream : aggregatedStreams) {
      if (!stream.incrementToken()) {
        continue;
      }
      restoreState(stream.captureState());
      return true;
    }

    // No stream has more token.
    return false;
  }

  @Override
  public void reset(CharSequence input) {
    Preconditions.checkNotNull(input);
    for (TokenStream stream : aggregatedStreams) {
      stream.reset(input);
    }
  }
}
