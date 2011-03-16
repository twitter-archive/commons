// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.base;

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
   * Returns an {@link ExceptionalSupplier}/{@link java.util.concurrent.Callable} object that
   * will return the result of {@code function} applied to {@code argument}.  Evaluation is lazy
   * and un-memoized.
   */
  public static <S, T, E extends Exception> CallableExceptionalSupplier<T, E> curry(
      final ExceptionalFunction<S, T, E> function, final S argument) {

    return new CallableExceptionalSupplier<T, E>() {
      @Override public T get() throws E {
        return function.apply(argument);
      }
    };
  }
}
