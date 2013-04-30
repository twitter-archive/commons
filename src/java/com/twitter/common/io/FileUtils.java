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

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import com.google.common.base.Preconditions;

import org.apache.commons.lang.SystemUtils;

import com.twitter.common.base.ExceptionalClosure;
import com.twitter.common.base.ExceptionalFunction;

/**
 * Utility methods for working with files and directories.
 *
 * @author John Sirois
 */
public final class FileUtils {

  /**
   * A utility for creating and working with temporary files and directories.
   */
  public static class Temporary {
    private static final int MAX_TMP_DIR_TRIES = 5;

    private final File basedir;

    /**
     * Creates a new temporary utility that creates files and directories rooted at {@code basedir}.
     *
     * @param basedir The base directory to generate temporary files and directories in.
     */
    public Temporary(File basedir) {
      Preconditions.checkNotNull(basedir);
      this.basedir = basedir;
    }

    /**
     * Returns a new empty temporary directory.
     *
     * @return a file representing the newly created directory.
     * @throws IllegalStateException if a new temporary directory could not be created
     */
    public File createDir() {
      File tempDir;
      int tries = 0;
      do {
        // For sanity sake, die eventually if we keep failing to pick a new unique directory name.
        if (++tries > MAX_TMP_DIR_TRIES) {
          throw new IllegalStateException("Failed to create a new temp directory in "
                                          + MAX_TMP_DIR_TRIES + " attempts, giving up");
        }
        tempDir = new File(basedir, UUID.randomUUID().toString());
      } while (!tempDir.mkdir());
      return tempDir;
    }

    /**
     * Creates a new empty temporary file.
     *
     * @return a new empty temporary file
     * @throws IOException if there was a problem creating a new temporary file
     */
    public File createFile() throws IOException {
      return createFile(".tempfile");
    }

    /**
     * Creates a new empty temporary file with the given filename {@code suffix}.
     *
     * @param suffix The suffix for the temporary file name
     * @return a new empty temporary file
     * @throws IOException if there was a problem creating a new temporary file
     */
    public File createFile(String suffix) throws IOException {
      return File.createTempFile(FileUtils.class.getName(), suffix, basedir);
    }

    /**
     * Creates a new temporary directory and executes the unit of {@code work} against it ensuring
     * the directory and its contents are removed after the work completes normally or abnormally.
     *
     * @param work The unit of work to execute against the new temporary directory.
     * @param <E> The type of exception this unit of work can throw.
     * @throws E bubbled transparently when the unit of work throws
     */
    public <E extends Exception> void doWithDir(final ExceptionalClosure<File, E> work)
        throws E {
      Preconditions.checkNotNull(work);
      doWithDir(new ExceptionalFunction<File, Void, E>() {
        @Override public Void apply(File dir) throws E {
          work.execute(dir);
          return null;
        }
      });
    }

    /**
     * Creates a new temporary directory and executes the unit of {@code work} against it ensuring
     * the directory and its contents are removed after the work completes normally or abnormally.
     *
     * @param work The unit of work to execute against the new temporary directory.
     * @param <T> The type of result this unit of work produces.
     * @param <E> The type of exception this unit of work can throw.
     * @return the result when the unit of work completes successfully
     * @throws E bubbled transparently when the unit of work throws
     */
    public <T, E extends Exception> T doWithDir(ExceptionalFunction<File, T, E> work)
        throws E {
      Preconditions.checkNotNull(work);
      return doWithTemp(createDir(), work);
    }

    /**
     * Creates a new temporary file and executes the unit of {@code work} against it ensuring
     * the file is removed after the work completes normally or abnormally.
     *
     * @param work The unit of work to execute against the new temporary file.
     * @param <E> The type of exception this unit of work can throw.
     * @throws E bubbled transparently when the unit of work throws
     * @throws IOException if there was a problem creating a new temporary file
     */
    public <E extends Exception> void doWithFile(final ExceptionalClosure<File, E> work)
        throws E, IOException {
      Preconditions.checkNotNull(work);
      doWithFile(new ExceptionalFunction<File, Void, E>() {
        @Override public Void apply(File dir) throws E {
          work.execute(dir);
          return null;
        }
      });
    }

    /**
     * Creates a new temporary file and executes the unit of {@code work} against it ensuring
     * the file is removed after the work completes normally or abnormally.
     *
     * @param work The unit of work to execute against the new temporary file.
     * @param <T> The type of result this unit of work produces.
     * @param <E> The type of exception this unit of work can throw.
     * @return the result when the unit of work completes successfully
     * @throws E bubbled transparently when the unit of work throws
     * @throws IOException if there was a problem creating a new temporary file
     */
    public <T, E extends Exception> T doWithFile(ExceptionalFunction<File, T, E> work)
        throws E, IOException {
      Preconditions.checkNotNull(work);
      return doWithTemp(createFile(), work);
    }

    private static <T, E extends Exception> T doWithTemp(File file,
        ExceptionalFunction<File, T, E> work) throws E {
      try {
        return work.apply(file);
      } finally {
        org.apache.commons.io.FileUtils.deleteQuietly(file);
      }
    }
  }

  /**
   * A temporary based at the default system temporary directory.
   */
  public static final Temporary SYSTEM_TMP = new Temporary(SystemUtils.getJavaIoTmpDir());

  /**
   * Returns a new empty temporary directory.
   *
   * @return a file representing the newly created directory.
   * @throws IllegalStateException if a new temporary directory could not be created
   */
  public static File createTempDir() {
    return SYSTEM_TMP.createDir();
  }

  private FileUtils() {
    // utility
  }
}
