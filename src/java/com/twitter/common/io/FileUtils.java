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

package com.twitter.common.io;

import org.apache.commons.lang.SystemUtils;

import java.io.File;
import java.util.UUID;

/**
 * Utility methods for working with files and directories.
 *
 * @author John Sirois
 */
public final class FileUtils {
  private static final int MAX_TMP_DIR_TRIES = 5;

  /**
   * Returns a new empty temporary directory.
   */
  public static File createTempDir() {
    File tempDir;
    int tries = 0;
    do {
      // For sanity sake, die eventually if we keep failing to pick a new unique directory name.
      if (++tries > MAX_TMP_DIR_TRIES) {
        throw new IllegalStateException("Failed to create a new temp directory in "
                                        + MAX_TMP_DIR_TRIES + " attempts, giving up");
      }
      tempDir = new File(SystemUtils.getJavaIoTmpDir(), UUID.randomUUID().toString());
    } while (!tempDir.mkdir());
    return tempDir;
  }

  private FileUtils() {
    // utility
  }
}
