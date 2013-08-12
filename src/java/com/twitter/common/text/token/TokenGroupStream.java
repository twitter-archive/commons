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

import java.util.List;

import org.apache.lucene.util.Attribute;
import org.apache.lucene.util.AttributeSource;

/**
 * A {@code TokenStream} used to access the member of a token group created by {@code TokenGrouper}.
 */
public class TokenGroupStream extends TokenStream {
  private List<AttributeSource.State> states;
  private int currentIndex;

  public TokenGroupStream(List<Class<? extends Attribute>> attributeClasses) {
    for (Class<? extends Attribute> attributeClass : attributeClasses) {
      addAttribute(attributeClass);
    }
    currentIndex = 0;
  }

  public void setStates(List<AttributeSource.State> states) {
    this.states = states;
    currentIndex = 0;
  }

  @Override
  public boolean incrementToken() {
    if (currentIndex >= states.size()) {
      return false;
    }

    restoreState(states.get(currentIndex));
    currentIndex++;

    return true;
  }

  /**
   * Resets this token group stream. input is discarded.
   */
  @Override
  public void reset(CharSequence input) {
    currentIndex = 0;
  }

  public int size() {
    return states.size();
  }

  public void accessTokenAt(int index) {
    restoreState(states.get(index));
  }
}
