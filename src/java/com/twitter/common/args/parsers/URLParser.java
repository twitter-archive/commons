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

package com.twitter.common.args.parsers;

import java.net.MalformedURLException;
import java.net.URL;

import com.twitter.common.args.ArgParser;

/**
 * URL parser.
 *
 * @author William Farner
 */
@ArgParser
public class URLParser extends NonParameterizedTypeParser<URL> {
  @Override
  public URL doParse(String raw) {
    try {
      return new URL(raw);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Malformed URL " + raw + ", " + e.getMessage());
    }
  }
}
