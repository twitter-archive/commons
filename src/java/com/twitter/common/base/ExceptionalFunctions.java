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

/**
 * Utility functions for working with exceptional functions.
 *
 * @author John Sirois
 */
public final class ExceptionalFunctions {

  private ExceptionalFunctions() {
    // utility
  }

  /**
   * Returns an {@link ExceptionalSupplier}/{@link java.util.concurrent.Callable} object that will
   * return the result of {@code function} applied to {@code argument}.  Evaluation is lazy and
   * un-memoized.
   */
  public static <S, T, E extends Exception> CallableExceptionalSupplier<T, E> curry(
      final ExceptionalFunction<S, T, E> function, final S argument) {

    return new CallableExceptionalSupplier<T, E>() {
      @Override
      public T get() throws E {
        return function.apply(argument);
      }
    };
  }

  /**
   * Returns an ExceptionalFunction that is a composition of multiple ExceptionalFunctions.
   */
  public static <T, E extends Exception> ExceptionalFunction<T, T, E> compose(
      final Iterable<ExceptionalFunction<T, T, E>> functions) {
    return new ExceptionalFunction<T, T, E>() {
      @Override
      public T apply(T input) throws E {
        T result = input;
        for (ExceptionalFunction<T, T, E> f : functions) {
          result = f.apply(result);
        }
        return result;
      }
    };
  }

  /**
   * Returns a List of ExceptionalFunctions from variable number of ExceptionalFunctions.
   */
  public static <T, E extends Exception> ExceptionalFunction<T, T, E> compose(
      ExceptionalFunction<T, T, E> function, ExceptionalFunction<T, T, E>... functions) {
    return compose(ImmutableList.<ExceptionalFunction<T, T, E>>builder()
        .add(function)
        .add(functions)
        .build());
  }

  /**
   * Returns a new ExceptionalFunction which composes two ExceptionalFunctions of compatible types.
   *
   * @param second function to apply to result of first.
   * @param first function to apply to input item.
   * @param <A> input type of first.
   * @param <B> input type of second.
   * @param <C> output type of second.
   * @param <E> exception type.
   * @return new composed ExceptionalFunction.
   */
  public static <A, B, C, E extends Exception> ExceptionalFunction<A, C, E> compose(
      final ExceptionalFunction<B, C, ? extends E> second,
      final ExceptionalFunction<A, ? extends B, ? extends E> first) {
    return new ExceptionalFunction<A, C, E>() {
      @Override
      public C apply(A item) throws E {
        return second.apply(first.apply(item));
      }
    };
  }

  /**
   * Builds an ExceptionalFunction from {@link com.google.common.base.Function}.
   *
   * @param function guava Function.
   * @param <S> input type.
   * @param <T> output type.
   * @param <E> exception type.
   * @return new ExceptionalFunction.
   */
  public static <S, T, E extends Exception> ExceptionalFunction<S, T, E> forFunction(
      final com.google.common.base.Function<S, T> function) {
    return new ExceptionalFunction<S, T, E>() {
      @Override
      public T apply(S item) {
        return function.apply(item);
      }
    };
  }

  /**
   * Builds an ExceptionalFunction from a return value. The returned ExceptionalFunction will always
   * return the given value.
   *
   * @param value value to return.
   * @param <S> input type.
   * @param <T> output type.
   * @param <E> exception type.
   * @return new ExceptionalFunction.
   */
  public static <S, T, E extends Exception> ExceptionalFunction<S, T, E> constant(
      final T value) {
    return new ExceptionalFunction<S, T, E>() {
      @Override
      public T apply(S item) throws E {
        return value;
      }
    };
  }

  /**
   * Builds an ExceptionalFunction from an Exception. The returned ExceptionalFunction will always
   * throw the given Exception.
   *
   * @param exception exception to throw.
   * @param <S> input type.
   * @param <T> output type.
   * @param <E> exception type.
   * @return new ExceptionalFunction.
   */
  public static <S, T, E extends Exception> ExceptionalFunction<S, T, E> forException(
      final E exception) {
    return new ExceptionalFunction<S, T, E>() {
      @Override
      public T apply(S item) throws E {
        throw exception;
      }
    };
  }
}
