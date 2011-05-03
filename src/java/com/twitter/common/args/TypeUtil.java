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
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Utility class to extract generic type information.
 *
 * TODO(William Farner): Move this into a common library, integrate with EasyMockTest.Clazz.
 *
 * @author William Farner
 */
public class TypeUtil {

  private static final Function<Type, Class<?>> GET_CLASS = new Function<Type, Class<?>>() {
    @Override public Class<?> apply(Type type) {
      if (type instanceof ParameterizedType) {
        return GET_CLASS.apply(((ParameterizedType) type).getRawType());
      }

      return (Class) type;
    }
  };

  private TypeUtil() {
    // Utility.
  }

  /**
   * Convenience method to call {@link #getTypeParamClasses(Type)} on a {@link Field}.
   *
   * @param field The field to extract type parameters from.
   * @return The raw classes of types that {@code field} is parameterized with.
   */
  public static List<Class<?>> getTypeParamClasses(Field field) {
    return getTypeParamClasses(field.getGenericType());
  }

  /**
   * Gets the clases that a type is type-parameterized with, in declaration order.
   *
   * @param type The type to extract type parameters from.
   * @return The raw classes of types that {@code field} is parameterized with.
   */
  public static List<Class<?>> getTypeParamClasses(Type type) {
    return Lists.transform(Arrays.asList(
        ((ParameterizedType) type).getActualTypeArguments()), GET_CLASS);
  }

  /**
   * Convenience method to call {@link #getTypeParamClasses(Field)}, with the requirement that there
   * is exactly one type parameter on the field.
   *
   * @param field The field to extract type parameters from.
   * @return The raw classes of types that {@code field} is parameterized with.
   */
  public static Class getTypeParamClass(Field field) {
    List<Class<?>> typeParams = getTypeParamClasses(field);
    Preconditions.checkArgument(typeParams.size() == 1,
        "Expected exactly one type parameter for field " + field);
    return typeParams.get(0);
  }

  /**
   * Gets the type parameter from a field.  Assumes that there is at least one type parameter.
   *
   * @param field The field to extract the type parameter from.
   * @return The field type parameter.
   */
  public static Type getTypeParam(Field field) {
    return ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
  }

  /**
   * A function to extract the type parameter class from fields.
   */
  public static Function<Field, Class> GET_TYPE_PARAM_CLASS = new Function<Field, Class>() {
    @Override public Class apply(Field field) {
      return getTypeParamClass(field);
    }
  };
}
