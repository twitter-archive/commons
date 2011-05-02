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
 * A convenience typedef that also ties into google's {@code Function}.
 *
 * @param <S> The argument type for the function.
 * @param <T> The return type for the function.
 *
 * @author John Sirois
 */
public interface Function<S, T>
    extends ExceptionalFunction<S, T, RuntimeException>, com.google.common.base.Function<S, T> {

  @Override
  T apply(S item);
}
