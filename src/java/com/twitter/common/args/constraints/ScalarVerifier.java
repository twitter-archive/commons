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

package com.twitter.common.args.constraints;

import java.lang.annotation.Annotation;

/**
 * Partial verifier implementation to simplify implementation of scalar verifiers. A scalar
 * verifier is one that requires no information other than the object in order to operate.
 *
 * @author William Farner
 */
public abstract class ScalarVerifier<T> implements Verifier<T> {

  abstract void verify(T value);

  @Override public void verify(T value, Annotation annotation) {
    verify(value);
  }
}
