// =================================================================================================
// Copyright 2013 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.logging.julbridge;

import java.util.logging.Level;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An utility class to convert between JUL and Log4j Levels. Mapping is as follows:
 * <ul>
 * <li>FINEST <-> TRACE</li>
 * <li>FINER -> DEBUG</li>
 * <li>FINE <-> DEBUG</li>
 * <li>INFO <-> INFO</li>
 * <li>WARNING <-> WARN</li>
 * <li>SEVERE <-> ERROR</li>
 * <li>SEVERE <- FATAL</li>
 * </ul>
 *
 * Unknowns levels are mapped to FINE/DEBUG
 */
public class JULBridgeLevelConverter {

  private JULBridgeLevelConverter() {}

  /**
   * Converts a JUL level into a Log4j level.
   *
   * @param level the JUL level to convert
   * @return a Log4j level
   * @throws NullPointerException if level is null
   */
  public static org.apache.log4j.Level toLog4jLevel(Level level) {
    checkNotNull(level);

    if (level == Level.FINEST) {
      return org.apache.log4j.Level.TRACE;
    } else if (level == Level.FINER) {
      return org.apache.log4j.Level.DEBUG;
    } else if (level == Level.FINE) {
      return org.apache.log4j.Level.DEBUG;
    } else if (level == Level.INFO) {
      return org.apache.log4j.Level.INFO;
    } else if (level == Level.WARNING) {
      return org.apache.log4j.Level.WARN;
    } else if (level == Level.SEVERE) {
      return org.apache.log4j.Level.ERROR;
    } else if (level == Level.ALL) {
      return org.apache.log4j.Level.ALL;
    } else if (level == Level.OFF) {
      return org.apache.log4j.Level.OFF;
    }

    return org.apache.log4j.Level.DEBUG;
  }

  /**
   * Converts a Log4j level into a JUL level.
   *
   * @param level the Log4j level to convert
   * @return a JUL level
   * @throws NullPointerException if level is null
   */
  public static Level fromLog4jLevel(org.apache.log4j.Level level) {
    checkNotNull(level);

    if (level == org.apache.log4j.Level.TRACE) {
      return Level.FINEST;
    } else if (level == org.apache.log4j.Level.DEBUG) {
      return Level.FINE;
    } else if (level == org.apache.log4j.Level.INFO) {
      return Level.INFO;
    } else if (level == org.apache.log4j.Level.WARN) {
      return Level.WARNING;
    } else if (level == org.apache.log4j.Level.ERROR) {
      return Level.SEVERE;
    } else if (level == org.apache.log4j.Level.FATAL) {
      return Level.SEVERE;
    } else if (level == org.apache.log4j.Level.ALL) {
      return Level.ALL;
    } else if (level == org.apache.log4j.Level.OFF) {
      return Level.OFF;
    }

    return Level.FINE;
  }
}
