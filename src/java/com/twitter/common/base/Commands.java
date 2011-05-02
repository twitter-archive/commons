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

package com.twitter.common.base;

import com.google.common.base.Preconditions;

/**
 * Utility functions for working with commands.
 *
 * @author John Sirois
 */
public final class Commands {

  /**
   * A command that does nothing when executed.
   */
  public static final Command NOOP = new Command() {
    @Override public void execute() {
      // noop
    }
  };

  private Commands() {
    // utility
  }

  /**
   * Converts a command into a supplier returning null.
   *
   * @return A supplier whose {@link com.twitter.common.base.Supplier#get()} will cause the given
   *     {@code command} to be executed and {@code null} to be returned.
   */
  public static <E extends Exception> ExceptionalSupplier<Void, E> asSupplier(
      final ExceptionalCommand<E> command) {
    Preconditions.checkNotNull(command);

    return new ExceptionalSupplier<Void, E>() {
      @Override public Void get() throws E {
        command.execute();
        return null;
      }
    };
  }
}
