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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.twitter.common.args.ArgParser;

/**
 * Date parser.
 *
 * @author William Farner
 */
@ArgParser
public class DateParser extends NonParameterizedTypeParser<Date> {

  private static final String FORMAT = "MM/dd/yyyy HH:mm";
  private static final SimpleDateFormat SIMPLE_FORMAT = new SimpleDateFormat(FORMAT);

  @Override
  public Date doParse(String raw) {
    try {
      return SIMPLE_FORMAT.parse(raw);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Failed to parse " + raw + " in format " + FORMAT);
    }
  }
}
