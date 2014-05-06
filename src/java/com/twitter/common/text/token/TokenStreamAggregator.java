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
 * Aggregates the outputs of multiple {@code TokenStreams} into a single {@code TwitterTokenStream}.
 */
public class TokenStreamAggregator extends TwitterTokenStream {
  private final List<TwitterTokenStream> aggregatedStreams;

  protected TokenStreamAggregator(TwitterTokenStream... streams) {
    aggregatedStreams = ImmutableList.copyOf(streams);

    // register all attributes of every stream
    for (TwitterTokenStream stream : streams) {
      Iterator<Class<? extends Attribute>> it = stream.getAttributeClassesIterator();
      while (it.hasNext()) {
        addAttribute(it.next());
      }
    }
  }

  /**
   * Creates a {@code TwitterTokenStream} that aggregates the outputs of a given set of
   * {@code TokenStreams}.
   *
   * @param streams TokenStreams to aggregate
   * @return an aggregated TwitterTokenStream
   */
  public static final TwitterTokenStream of(TwitterTokenStream... streams) {
    return new TokenStreamAggregator(streams);
  }

  @Override
  public final boolean incrementToken() {
    for (TwitterTokenStream stream : aggregatedStreams) {
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
  public void reset() {
    CharSequence input = inputCharSequence();
    Preconditions.checkNotNull(input);
    for (TwitterTokenStream stream : aggregatedStreams) {
      stream.reset(input);
    }
  }
}
