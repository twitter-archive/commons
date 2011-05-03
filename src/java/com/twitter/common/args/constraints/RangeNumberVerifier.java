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

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies that a number (inclusively) lies within a range.
 *
 * @author William Farner
 */
public class RangeNumberVerifier implements Verifier<Number> {
  @Override
  public void verify(Number value, Annotation annotation) {
    checkArgument(annotation instanceof Range, "Annotation is not a range: " + annotation);

    Range range = (Range) annotation;

    checkArgument(range.lower() < range.upper(),
        "Range lower bound must be greater than upper bound.");

    double dblValue = value.doubleValue();
    checkArgument(dblValue >= range.lower() && dblValue <= range.upper(),
        String.format("Value must be in range [%f, %f]", range.lower(), range.upper()));
  }
}
