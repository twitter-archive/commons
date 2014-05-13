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

import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkNotNull;

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
   *         {@code command} to be executed and {@code null} to be returned.
   */
  public static <E extends Exception> ExceptionalSupplier<Void, E> asSupplier(
      final ExceptionalCommand<E> command) {
    checkNotNull(command);

    return new ExceptionalSupplier<Void, E>() {
      @Override public Void get() throws E {
        command.execute();
        return null;
      }
    };
  }

  /**
   * Combines multiple {@code commands} into a single command. A {@link RuntimeException} thrown
   * during the execution of one of the commands will prevent the subsequent commands from being
   * executed.
   *
   * @param commands Commands to compound.
   * @return A command whose {@link Command#execute()} will cause the given {@code commands} to be
   *         executed serially.
   */
  public static Command compound(Iterable<Command> commands) {
    final ImmutableList<Command> executableCommands = ImmutableList.copyOf(commands);
    return new Command() {
      @Override public void execute() {
        for (Command command : executableCommands) {
          command.execute();
        }
      }
    };
  }
}
