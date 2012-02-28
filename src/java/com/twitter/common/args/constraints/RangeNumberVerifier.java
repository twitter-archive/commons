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
import java.math.BigDecimal;

import com.google.common.base.Function;
import com.google.common.base.Functions;

import com.twitter.common.args.Verifier;
import com.twitter.common.args.VerifierFor;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies that a number (inclusively) lies within a range.
 *
 * @author William Farner
 */
@VerifierFor(Range.class)
public class RangeNumberVerifier implements Verifier<Number> {
  @Override
  public void verify(Number value, Annotation annotation) {
    Range range = getRange(annotation);

    checkArgument(range.lower() < range.upper(),
        "Range lower bound must be greater than upper bound.");

    double dblValue = value.doubleValue();
    checkArgument(dblValue >= range.lower() && dblValue <= range.upper(),
        String.format("Value must be in range [%f, %f]", range.lower(), range.upper()));
  }

  @Override
  public String toString(Class<? extends Number> argType, Annotation annotation) {
    Range range = getRange(annotation);

    Function<Number, Number> converter;
    if (Float.class.isAssignableFrom(argType)
        || Double.class.isAssignableFrom(argType)
        || BigDecimal.class.isAssignableFrom(argType)) {

      converter = Functions.identity();
    } else {
      converter = new Function<Number, Number>() {
        @Override public Number apply(Number item) {
          return item.longValue();
        }
      };
    }

    return String.format("must be >= %s and <= %s",
                         converter.apply(range.lower()),
                         converter.apply(range.upper()));
  }

  private Range getRange(Annotation annotation) {
    checkArgument(annotation instanceof Range, "Annotation is not a range: " + annotation);

    return (Range) annotation;
  }
}
