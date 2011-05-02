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

package com.twitter.common.util;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for working with java {@link Date}s.
 *
 * @author John Sirois
 */
public final class DateUtils {

  public static Date now() {
    return new Date();
  }

  public static long toUnixTime(Date date) {
    return toUnixTime(date.getTime());
  }

  public static long nowUnixTime() {
    return toUnixTime(System.currentTimeMillis());
  }

  public static long toUnixTime(long millisSinceEpoch) {
    return TimeUnit.MILLISECONDS.toSeconds(millisSinceEpoch);
  }

  public static Date ago(int calendarField, int amount) {
    return ago(now(), calendarField, amount);
  }

   public static Date ago(Date referenceDate, int calendarField, int amount) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(referenceDate);
    calendar.add(calendarField, -1 * amount);
    return calendar.getTime();
  }

  private DateUtils() {
    // utility
  }
}
