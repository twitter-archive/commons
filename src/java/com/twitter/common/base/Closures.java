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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/**
 * Utilities for dealing with Closures.
 *
 * @author John Sirois
 */
public final class Closures {

  private static final Closure NOOP = new Closure() {
    @Override public void execute(Object item) {
      // noop
    }
  };

  private Closures() {
    // utility
  }

  /**
   * Converts a closure into a function returning {@code null}.
   */
  public static <T> Function<T, Void> asFunction(final ExceptionalClosure<T, ?> closure) {
    Preconditions.checkNotNull(closure);

    return new Function<T, Void>() {
      @Override public Void apply(T item) {
        try {
          closure.execute(item);
        } catch (Exception e) {
          Throwables.propagate(e);
        }
        return null;
      }
    };
  }

  /**
   * Returns a closure that will do nothing.
   *
   * @param <T> The closure argument type.
   * @return A closure that does nothing.
   */
  @SuppressWarnings("unchecked")
  public static <T> Closure<T> noop() {
    return NOOP;
  }
}
