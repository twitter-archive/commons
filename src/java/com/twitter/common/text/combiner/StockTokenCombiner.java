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

import com.twitter.common.text.detector.PunctuationDetector;
import com.twitter.common.text.extractor.RegexExtractor;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.TokenType;

/**
 * Combines multiple tokens denoting a stock symbol (e.g., $YHOO) back into a single token.
 */
public class StockTokenCombiner extends ExtractorBasedTokenCombiner {
  private static final Pattern STOCK_SYMBOL_PATTERN =
    Pattern.compile("(?:^|" + PunctuationDetector.PUNCTUATION_REGEX
                    + "|" + PunctuationDetector.SPACE_REGEX
                    + ")(\\$[a-zA-Z]{1,6}(:[a-zA-Z0-9]{1,6})?)(?=$"
                    + "|" + PunctuationDetector.SPACE_REGEX
                    + "|" + PunctuationDetector.PUNCTUATION_REGEX + ")");

  public StockTokenCombiner(TokenStream inputStream) {
    super(inputStream);
    setExtractor(new RegexExtractor.Builder()
                                   .setRegexPattern(STOCK_SYMBOL_PATTERN, 1, 1)
                                   .build());
    setType(TokenType.STOCK);
  }
}
