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

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import com.twitter.common.args.constraints.CanRead;
import com.twitter.common.args.constraints.CanReadFileVerifier;
import com.twitter.common.args.constraints.CanWrite;
import com.twitter.common.args.constraints.CanWriteFileVerifier;
import com.twitter.common.args.constraints.Exists;
import com.twitter.common.args.constraints.ExistsFileVerifier;
import com.twitter.common.args.constraints.IsDirectory;
import com.twitter.common.args.constraints.IsDirectoryFileVerifier;
import com.twitter.common.args.constraints.NotEmpty;
import com.twitter.common.args.constraints.NotEmptyStringVerifier;
import com.twitter.common.args.constraints.NotNegative;
import com.twitter.common.args.constraints.NotNegativeNumberVerifier;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.args.constraints.NotNullVerifier;
import com.twitter.common.args.constraints.Positive;
import com.twitter.common.args.constraints.PositiveNumberVerifier;
import com.twitter.common.args.constraints.Range;
import com.twitter.common.args.constraints.RangeNumberVerifier;
import com.twitter.common.args.constraints.Verifier;
import com.twitter.common.collections.Pair;

/**
 * Utility class to manage relationships between constraints and types.
 *
 * TODO(William Farner): Include a mechanism for callers to use custom verifiers without having to
 *    register them here.
 *
 * @author William Farner
 */
public class Constraints {

  @SuppressWarnings("unchecked")
  public static <T> Verifier<T> get(final Class<T> type, Annotation constraint) {
    for (Map.Entry<Pair<Class<?>, Class<Annotation>>, Verifier> entry : REGISTRY.entrySet()) {
      if (entry.getKey().getSecond() == constraint.annotationType()
          && entry.getKey().getFirst().isAssignableFrom(type)) {
        return (Verifier<T>) entry.getValue();
      }
    }

    return null;
  }

  @SuppressWarnings("unchecked")
  private static final Map<Pair<Class<?>, Class<Annotation>>, Verifier> REGISTRY =
      ImmutableMap.<Pair<Class<?>, Class<Annotation>>, Verifier>builder()
      .put(new Pair(Object.class, NotNull.class), new NotNullVerifier())
      .put(new Pair(Number.class, Positive.class), new PositiveNumberVerifier())
      .put(new Pair(Number.class, NotNegative.class), new NotNegativeNumberVerifier())
      .put(new Pair(Number.class, Range.class), new RangeNumberVerifier())
      .put(new Pair(byte.class, Positive.class), new PositiveNumberVerifier())
      .put(new Pair(byte.class, NotNegative.class), new NotNegativeNumberVerifier())
      .put(new Pair(byte.class, Range.class), new RangeNumberVerifier())
      .put(new Pair(short.class, Positive.class), new PositiveNumberVerifier())
      .put(new Pair(short.class, NotNegative.class), new NotNegativeNumberVerifier())
      .put(new Pair(short.class, Range.class), new RangeNumberVerifier())
      .put(new Pair(int.class, Positive.class), new PositiveNumberVerifier())
      .put(new Pair(int.class, NotNegative.class), new NotNegativeNumberVerifier())
      .put(new Pair(int.class, Range.class), new RangeNumberVerifier())
      .put(new Pair(long.class, Positive.class), new PositiveNumberVerifier())
      .put(new Pair(long.class, NotNegative.class), new NotNegativeNumberVerifier())
      .put(new Pair(long.class, Range.class), new RangeNumberVerifier())
      .put(new Pair(float.class, Positive.class), new PositiveNumberVerifier())
      .put(new Pair(float.class, NotNegative.class), new NotNegativeNumberVerifier())
      .put(new Pair(float.class, Range.class), new RangeNumberVerifier())
      .put(new Pair(double.class, Positive.class), new PositiveNumberVerifier())
      .put(new Pair(double.class, NotNegative.class), new NotNegativeNumberVerifier())
      .put(new Pair(double.class, Range.class), new RangeNumberVerifier())
      .put(new Pair(String.class, NotEmpty.class), new NotEmptyStringVerifier())
      .put(new Pair(File.class, Exists.class), new ExistsFileVerifier())
      .put(new Pair(File.class, CanRead.class), new CanReadFileVerifier())
      .put(new Pair(File.class, CanWrite.class), new CanWriteFileVerifier())
      .put(new Pair(File.class, IsDirectory.class), new IsDirectoryFileVerifier())
      .build();
}
