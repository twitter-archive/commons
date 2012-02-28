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

import com.twitter.common.args.Verifier;
import com.twitter.common.args.VerifierFor;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A verifier that ensures a value is non-null.
 *
 * @author William Farner
 */
@VerifierFor(NotNull.class)
public class NotNullVerifier implements Verifier<Object> {
  @Override
  public void verify(Object value, Annotation annotation) {
    checkArgument(value != null, "Value must not be null.");
  }

  @Override
  public String toString(Class<?> argType, Annotation annotation) {
    return "not null";
  }
}
