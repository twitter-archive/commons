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

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.TokenType;

/**
 * Combines contracted word followed by dot/period (e.g., Mr. Inc.) back into a single token.
 */
public class DotContractedTokenCombiner extends LookAheadTokenCombiner {
  private Set<String> contractedWords = ImmutableSet.of(
      "mr", "mrs", "ms", "dr", "drs", "prof",
      "gen", "rep", "sen", "st", "jr", "sr",   // social titles
      "inc", "co", "corp", "ltd",              // company names
      "no",                                    // special list
      "mon", "tue", "wed", "thu", "fri", "sat", "sun", // day of a week
      "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec" // months
      );

  public DotContractedTokenCombiner(TokenStream inputStream) {
    super(inputStream);
    setType(TokenType.TOKEN);
  }

  @Override
  public boolean canBeCombinedWithNextToken(CharSequence term) {
    return contractedWords.contains(term.toString().toLowerCase());
  }

  @Override
  public boolean canBeCombinedWithPreviousToken(CharSequence term) {
    return term.length() == 1 && term.charAt(0) == '.';
  }
}
