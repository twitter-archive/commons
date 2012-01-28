// =================================================================================================
// Copyright 2012 Twitter, Inc.
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

package com.twitter.common.reflect;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.google.common.base.Preconditions;

/**
 * A class meant to be sub-classed in order to capture a generic type literal value.  To capture
 * the type of a {@code List<String>} you would use: {@code new TypeToken<List<String>>() {}}.
 *
 * <p>Types can also be captured directly using the {@link #create} static factory methods.
 *
 * <p>See Neil Gafter's
 * <a href="http://gafter.blogspot.com/2006/12/super-type-tokens.html">post</a> about this idea.
 *
 * @param <T> The (possibly parameterized) type to be captured.
 *
 * @author John Sirois
 */
public abstract class TypeToken<T> {

  /**
   * The type captured by this type token.
   */
  protected final Type type;

  protected TypeToken() {
    Type superclass = getClass().getGenericSuperclass();
    try {
      type = extractTypeToken(superclass);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(e);
    }
  }

  private TypeToken(Type type) {
    this.type = type;
  }

  /**
   * Returns the type captured by this type token.
   *
   * @return The captured type.
   */
  public Type getType() {
    return type;
  }

  /**
   * Implements value-equality over the this type token's captured type.
   *
   * @param o Any object.
   * @return {@code true} if {@code o} is a type token that captures the exact same type as this
   *     type token.
   */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TypeToken)) {
      return false;
    }

    TypeToken other = (TypeToken) o;
    return type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public String toString() {
    return String.format("TypeToken<%s>",
        type instanceof Class ? ((Class<?>) type).getName() : type.toString());
  }

  /**
   * Creates a type token for the given raw class.
   *
   * @param cls The class to extract type information from.
   * @return A type token representing the raw class type.
   */
  public static <T> TypeToken<T> create(final Class<T> cls) {
    Preconditions.checkNotNull(cls);
    return new TypeToken<T>(cls) { };
  }

  /**
   * Creates a type token wrapping the given type.
   *
   * @param type The type to wrap in a type token.
   * @return A type token wrapping the type.
   */
  public static TypeToken<?> create(final Type type) {
    Preconditions.checkNotNull(type);
    return new TypeToken(type) { };
  }

  /**
   * Extracts the actual type parameter for a singly parameterized type.
   *
   * @param type The parameterized type to extract the type argument from.
   * @return The type of the single specified type parameter for {@code type}.
   * @throws IllegalArgumentException if the supplied type does not have exactly one specified type
   *     parameter
   */
  public static Type extractTypeToken(Type type) {
    Preconditions.checkNotNull(type);
    Preconditions.checkArgument(type instanceof ParameterizedType, "Missing type parameter.");
    Type[] typeArguments = ((ParameterizedType) type).getActualTypeArguments();
    Preconditions.checkArgument(typeArguments.length == 1,
        "Expected a type with exactly 1 type argument");
    return typeArguments[0];
  }
}
