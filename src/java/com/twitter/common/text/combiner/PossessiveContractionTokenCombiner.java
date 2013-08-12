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

import java.util.regex.Pattern;

import com.twitter.common.text.extractor.RegexExtractor;
import com.twitter.common.text.token.TokenStream;

/**
 * Combines multiple tokens denoting a possessive form (e.g., Twitter's) or
 * a contraction form (e.g., isn't) back into a single token.
 */
public class PossessiveContractionTokenCombiner extends ExtractorBasedTokenCombiner {
  private static final Pattern APOSTROPHE_S = Pattern.compile("([a-zA-Z]+'(?i:t|s|m|re|ve|ll|d))([^a-zA-Z]|$)");

  public PossessiveContractionTokenCombiner(TokenStream inputStream) {
    super(inputStream);
    setExtractor(new RegexExtractor.Builder().setRegexPattern(APOSTROPHE_S, 1, 1)
                     .setTriggeringChar('\'').build());
  }
}
