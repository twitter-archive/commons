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
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utilities for dealing with Closures.
 *
 * @author John Sirois
 */
public final class Closures {

  private static final Closure<?> NOOP = new Closure<Object>() {
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
    checkNotNull(closure);

    // CHECKSTYLE:OFF IllegalCatch
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
    // CHECKSTYLE:ON IllegalCatch
  }

  /**
   * Varargs equivalent of {@link #combine(Iterable)}.
   *
   * @param closures Closures to combine.
   * @param <T> Type accepted by the closures.
   * @return A single closure that will fan out all calls to {@link Closure#execute(Object)} to
   *    the wrapped closures.
   */
  public static <T> Closure<T> combine(Closure<T>... closures) {
    return combine(ImmutableList.copyOf(closures));
  }

  /**
   * Combines multiple closures into a single closure, whose calls are replicated sequentially
   * in the order that they were provided.
   * If an exception is encountered from a closure it propagates to the top-level closure and the
   * remaining closures are not executed.
   *
   * @param closures Closures to combine.
   * @param <T> Type accepted by the closures.
   * @return A single closure that will fan out all calls to {@link Closure#execute(Object)} to
   *    the wrapped closures.
   */
  public static <T> Closure<T> combine(Iterable<Closure<T>> closures) {
    checkNotNull(closures);
    checkArgument(Iterables.all(closures, Predicates.notNull()));

    final Iterable<Closure<T>> closuresCopy = ImmutableList.copyOf(closures);

    return new Closure<T>() {
      @Override public void execute(T item) {
        for (Closure<T> closure : closuresCopy) {
          closure.execute(item);
        }
      }
    };
  }

  /**
   * Applies a filter to a closure, such that the closure will only be called when the filter is
   * satisfied (returns {@code true}}.
   *
   * @param filter Filter to determine when {@code closure} is called.
   * @param closure Closure to filter.
   * @param <T> Type handled by the filter and the closure.
   * @return A filtered closure.
   */
  public static <T> Closure<T> filter(final Predicate<T> filter, final Closure<T> closure) {
    checkNotNull(filter);
    checkNotNull(closure);

    return new Closure<T>() {
      @Override public void execute(T item) {
        if (filter.apply(item)) {
          closure.execute(item);
        }
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
    return (Closure<T>) NOOP;
  }
}
