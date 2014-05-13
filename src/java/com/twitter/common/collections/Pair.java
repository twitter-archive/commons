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

package com.twitter.common.collections;

import javax.annotation.Nullable;

import com.google.common.base.Function;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;


/**
 * An immutable 2-tuple with value-equals semantics.
 *
 * @param <A> The type of the 1st item in the pair.
 * @param <B> The type of the 2nd item in the pair.
 *
 * @author William Farner
 */
public class Pair<A, B> {

  @Nullable
  private final A first;
  @Nullable
  private final B second;

  /**
   * Creates a new pair.
   *
   * @param first The first value.
   * @param second The second value.
   */
  public Pair(@Nullable A first, @Nullable B second) {
    this.first = first;
    this.second = second;
  }

  @Nullable
  public A getFirst() {
    return first;
  }

  @Nullable
  public B getSecond() {
    return second;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) { return true; }
    if (!(o instanceof Pair)) { return false; }

    Pair<?, ?> that = (Pair<?, ?>) o;
    return new EqualsBuilder()
        .append(this.first, that.first)
        .append(this.second, that.second)
        .isEquals();
  }

  @Override
  public String toString() {
    return String.format("(%s, %s)", getFirst(), getSecond());
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(first)
        .append(second)
        .toHashCode();
  }

  /**
   * Creates a function that can extract the first item of pairs of the given type parametrization.
   *
   * @param <S> The type of the 1st item in the pair.
   * @param <T> The type of the 2nd item in the pair.
   * @return A function that will extract the 1st item in a pair.
   */
  public static <S, T> Function<Pair<S, T>, S> first() {
    return new Function<Pair<S, T>, S>() {
      @Override public S apply(Pair<S, T> pair) {
        return pair.first;
      }
    };
  }

  /**
   * Creates a function that can extract the second item of pairs of the given type parametrization.
   *
   * @param <S> The type of the 1st item in the pair.
   * @param <T> The type of the 2nd item in the pair.
   * @return A function that will extract the 2nd item in a pair.
   */
  public static <S, T> Function<Pair<S, T>, T> second() {
    return new Function<Pair<S, T>, T>() {
      @Override public T apply(Pair<S, T> pair) {
        return pair.second;
      }
    };
  }

  /**
   * Convenience method to create a pair.
   *
   * @param a The first value.
   * @param b The second value.
   * @param <A> The type of the 1st item in the pair.
   * @param <B> The type of the 2nd item in the pair.
   * @return A new pair of [a, b].
   */
  public static <A, B> Pair<A, B> of(@Nullable A a, @Nullable B b) {
    return new Pair<A, B>(a, b);
  }
}
