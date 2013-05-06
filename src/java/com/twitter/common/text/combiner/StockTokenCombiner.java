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

import com.twitter.Regex;
import com.twitter.common.text.extractor.RegexExtractor;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.TokenType;

/**
 * Combines multiple tokens denoting a stock symbol (e.g., $YHOO) back into a single token.
 */
public class StockTokenCombiner extends ExtractorBasedTokenCombiner {
  // Regex.VALID_CASHTAG in twitter-text doesn't capture $ symbol, so we need to modify the regex
  // to capture $ symbol.
  public static final Pattern STOCK_SYMBOL_PATTERN =
    Pattern.compile(Regex.VALID_CASHTAG.toString().replace(")\\$(", ")(\\$)("),
                    Pattern.CASE_INSENSITIVE);

  public StockTokenCombiner(TokenStream inputStream) {
    super(inputStream);
    setExtractor(new RegexExtractor.Builder()
                                   .setRegexPattern(STOCK_SYMBOL_PATTERN, 1, 2)
                                   .build());
    setType(TokenType.STOCK);
  }
}
