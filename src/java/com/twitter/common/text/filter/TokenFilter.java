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

package com.twitter.common.text.filter;

import com.twitter.common.text.token.TokenProcessor;
import com.twitter.common.text.token.TwitterTokenStream;

/**
 * Filters out tokens from a given {@code TwitterTokenStream}.
 */
public abstract class TokenFilter extends TokenProcessor {
  public TokenFilter(TwitterTokenStream inputStream) {
    super(inputStream);
  }

  @Override
  public final boolean incrementToken() {
    while (incrementInputStream()) {
      if (acceptToken()) {
        return true;
      }
    }

    return false;
  }

 /**
 * Overwrite this method to control which tokens are filtered out.
 *
 * @return {@code true} to accept the current token, {@code false} otherwise
 */
  public abstract boolean acceptToken();
}
