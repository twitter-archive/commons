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

package com.twitter.common.util.logging;

import java.util.logging.LogManager;

/**
 * A LogManager which by default ignores calls to {@link #reset()}.  This is useful to avoid missing
 * log statements that occur during vm shutdown.  The standard LogManager installs a
 * {@link Runtime#addShutdownHook(Thread) shutdown hook} that disables logging and this subclass
 * nullifies that shutdown hook by disabling any reset of the LogManager by default.
 *
 * @author John Sirois
 */
public class UnresettableLogManager extends LogManager {

  /**
   * The system property that controls which LogManager the java.util.logging subsystem should load.
   */
  public static final String LOGGING_MANAGER = "java.util.logging.manager";

  /**
   * A system property which can be used to control an {@code UnresettableLogManager}'s behavior.
   * If the UnresettableLogManager is installed, but an application still wants
   * {@link LogManager#reset()} behavior, they can set this property to "false".
   */
  private static final String LOGGING_MANAGER_IGNORERESET = "java.util.logging.manager.ignorereset";

  @Override
  public void reset() throws SecurityException {
    if (Boolean.parseBoolean(System.getProperty(LOGGING_MANAGER_IGNORERESET, "true"))) {
      System.err.println("UnresettableLogManager is ignoring a reset() request.");
    } else {
      super.reset();
    }
  }
}
