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

package com.twitter.common.text.extractor;

import com.twitter.Regex;

/**
 * Extracts hashtags from text, according to the canonical definition found in the
 * {@code twitter-text-java} library {@link com.twitter.Regex}.
 */
public class HashtagExtractor extends RegexExtractor {
  /** Default constructor. **/
  public HashtagExtractor() {
    setRegexPattern(Regex.VALID_HASHTAG,
        Regex.VALID_HASHTAG_GROUP_HASH,
        Regex.VALID_HASHTAG_GROUP_TAG);
    setTriggeringChar('#');
  }
}
