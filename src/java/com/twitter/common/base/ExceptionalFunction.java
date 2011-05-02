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

/**
 * An interface that captures a unit of work against an item.
 *
 * @param <S> The argument type for the function.
 * @param <T> The return type for the function.
 * @param <E> The exception type that the function throws.
 *
 * @author John Sirois
 */
public interface ExceptionalFunction<S, T, E extends Exception> {

  /**
   * Performs a unit of work on item, possibly throwing {@code E} in the process.
   *
   * <p>TODO(John Sirois): consider supporting @Nullable
   *
   * @param item The item to perform work against.
   * @return The result of the computation.
   * @throws E if there was a problem performing the work.
   */
  T apply(S item) throws E;
}
