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

package com.twitter.common.logging;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;

import java.io.File;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Logging utility functions.
 *
 * @author William Farner
 */
public class LogUtil {

  private static final Logger LOG = Logger.getLogger(LogUtil.class.getName());

  private static final String LOG_MANAGER_FILE_PROP = "java.util.logging.FileHandler.pattern";

  @VisibleForTesting
  static final File DEFAULT_LOG_DIR = new File("/var/log");

  /**
   * Gets the log directory as configured with the log manager.  This will attempt to expand any
   * directory wildcards that are included in log file property.
   *
   * @return The configured log directory.
   */
  public static File getLogManagerLogDir() {
    return getLogManagerLogDir(LogManager.getLogManager().getProperty(LOG_MANAGER_FILE_PROP));
  }

  /**
   * Gets the log directory as specified in a log file pattern.  This will attempt to expand any
   * directory wildcards that are included in log file property.
   *
   * @param logFilePattern The pattern to extract the log directory from.
   * @return The configured log directory.
   */
  public static File getLogManagerLogDir(String logFilePattern) {
    if (StringUtils.isEmpty(logFilePattern)) {
      LOG.warning("Could not find log dir in logging property " + LOG_MANAGER_FILE_PROP
                  + ", reading from " + DEFAULT_LOG_DIR);
      return DEFAULT_LOG_DIR;
    }

    String logDir = expandWildcard(logFilePattern, "%t", SystemUtils.JAVA_IO_TMPDIR);
    logDir = expandWildcard(logDir, "%h", SystemUtils.USER_HOME);
    File parent = new File(logDir).getParentFile();
    return parent == null ? new File(".") : parent;
  }

  /**
   * Expands a directory path wildcard within a file pattern string.
   * Correctly handles cases where the replacement string does and does not contain a trailing
   * slash.
   *
   * @param pattern File pattern string, which may or may not contain a wildcard.
   * @param dirWildcard Wildcard string to expand.
   * @param replacement Path component to expand wildcard to.
   * @return {@code pattern} with all instances of {@code dirWildcard} replaced with
   *     {@code replacement}.
   */
  private static String expandWildcard(String pattern, String dirWildcard, String replacement) {
    String replace = dirWildcard;
    if (replacement.charAt(replacement.length() - 1) == '/') {
      replace += '/';
    }
    return pattern.replaceAll(replace, replacement);
  }

  private LogUtil() {
    // Utility class.
  }
}
