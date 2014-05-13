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

package com.twitter.common.args;

import java.lang.annotation.Annotation;

/**
 * Typedef for a constraint verifier.
 */
public interface Verifier<T> {
  /**
   * Verifies the value against the annotation.
   *
   * @param value Value that is being applied.
   * @throws IllegalArgumentException if the value is invalid.
   */
  void verify(T value, Annotation annotation) throws IllegalArgumentException;

  /**
   * Returns a representation of the constraint this verifier checks.
   *
   * @param argType The type of the {@link com.twitter.common.args.Arg} this annotation applies to.
   * @return A representation of the constraint this verifier checks.
   */
  String toString(Class<? extends T> argType, Annotation annotation);
}
