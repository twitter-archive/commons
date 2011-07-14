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
 * Extracts user names ('@'+{username}) from text, according to the canonical definition found in
 * the {@code twitter-text-java} library {@link com.twitter.Regex}.
 */
public class UserNameExtractor extends RegexExtractor {
  /** Default constructor. **/
  public UserNameExtractor() {
    setRegexPattern(Regex.AUTO_LINK_USERNAMES_OR_LISTS,
        Regex.AUTO_LINK_USERNAME_OR_LISTS_GROUP_AT,
        Regex.AUTO_LINK_USERNAME_OR_LISTS_GROUP_USERNAME);
    setTriggeringChar('@');
  }
}
