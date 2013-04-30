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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Creates a duplicate a {@code TokenStream}.
 *
 * A {@code TokenStream} returned by {@code duplicate} method provides the same output as
 * the original {@code TokenStream}, but without analyzing the input {@code CharSequence} again.
 */
public class TokenStreamDuplicator extends TokenProcessor {
  private final TokenStream inputStream;
  private final List<State> states = Lists.newArrayList();
  private final List<DuplicatedTokenStream> duplicatedStreams = Lists.newArrayList();

  private Iterator<State> stateIt = null;

  /**
   * Constructs a new TokenStreamDuplicator.
   *
   * @param inputStream TokenStream to duplicate.
   */
  public TokenStreamDuplicator(TokenStream inputStream) {
    super(inputStream);
    this.inputStream = inputStream;
  }

  @Override
  public void reset(CharSequence input) {
    super.reset(input);

    // capture all states from input stream
    states.clear();
    while (incrementInputStream()) {
      states.add(captureState());
    }
    stateIt = states.iterator();

    // updates the states in duplicated streams
    for (DuplicatedTokenStream stream : duplicatedStreams) {
      stream.resetStates(states);
    }
  }

  @Override
  public boolean incrementToken() {
    if (stateIt == null || !stateIt.hasNext()) {
      return false;
    }
    restoreState(stateIt.next());
    return true;
  }

  /**
   * Returns a new TokenStream which provides the same
   * output as the original TokenStream.
   *
   * @return a duplicated TokenStream
   */
  public TokenStream duplicate() {
    DuplicatedTokenStream duplicate = new DuplicatedTokenStream(inputStream);
    duplicatedStreams.add(duplicate);

    return duplicate;
  }

  protected static final class DuplicatedTokenStream extends TokenProcessor {
    private Iterator<State> stateIt = null;

    protected DuplicatedTokenStream(TokenStream inputStream) {
      super(inputStream);
    }

    @Override
    public boolean incrementToken() {
      if (stateIt == null || !stateIt.hasNext()) {
        return false;
      }
      restoreState(stateIt.next());
      return true;
    }

    @Override
    public void reset(CharSequence input) {
      // do nothing...
    }

    protected void resetStates(List<State> states) {
      // create Iterator which is independent from the original list of states.
      stateIt = ImmutableList.copyOf(states).iterator();
    }
  }
}
