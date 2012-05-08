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

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;

/**
 * Utility class to extract generic type information.
 *
 * TODO(William Farner): Move this into a common library, integrate with EasyMockTest.Clazz.
 *
 * @author William Farner
 */
public final class TypeUtil {

  private static final Function<Type, Type> GET_TYPE = new Function<Type, Type>() {
    @Override public Type apply(Type type) {
      if (type instanceof WildcardType) {
        return apply(((WildcardType) type).getUpperBounds()[0]);
      }
      return type;
    }
  };

  private TypeUtil() {
    // Utility.
  }

  /**
   * Gets the types that a type is type-parameterized with, in declaration order.
   *
   * @param type The type to extract type parameters from.
   * @return The types that {@code type} is parameterized with.
   */
  public static List<Type> getTypeParams(Type type) {
    if (type instanceof WildcardType) {
      return getTypeParams(GET_TYPE.apply(type));
    }
    return Lists.transform(Arrays.asList(
        ((ParameterizedType) type).getActualTypeArguments()), GET_TYPE);
  }

  /**
   * Finds the raw class of type.
   *
   * @param type The type to get the raw class of.
   * @return The raw class of type.
   */
  public static Class<?> getRawType(Type type) {
    if (type instanceof ParameterizedType) {
      return getRawType(((ParameterizedType) type).getRawType());
    }
    if (type instanceof WildcardType) {
      return getRawType(((WildcardType) type).getUpperBounds()[0]);
    }
    return (Class<?>) type;
  }

  /**
   * Convenience method to call {@link #getTypeParam(Field)}, with the requirement that there
   * is exactly one type parameter on the field.
   *
   * @param field The field to extract type parameters from.
   * @return The raw classes of types that {@code field} is parameterized with.
   */
  public static TypeToken<?> getTypeParamTypeToken(Field field) {
    List<Type> typeParams = getTypeParams(field.getGenericType());
    Preconditions.checkArgument(typeParams.size() == 1,
        "Expected exactly one type parameter for field " + field);
    return TypeToken.of(typeParams.get(0));
  }

  /**
   * Gets the type parameter from a field.  Assumes that there is at least one type parameter.
   *
   * @param field The field to extract the type parameter from.
   * @return The field type parameter.
   */
  public static Type getTypeParam(Field field) {
    return extractTypeToken(field.getGenericType());
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
