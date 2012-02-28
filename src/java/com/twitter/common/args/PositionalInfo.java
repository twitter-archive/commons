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

package com.twitter.common.args;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import com.twitter.common.args.apt.Configuration;
import com.twitter.common.reflect.TypeToken;

/**
 * Description of a positional command line argument.
 *
 * @author Nick Kallen
 */
public class PositionalInfo<T> extends ArgumentInfo<List<T>> {
  private final String canonicalName;
  private final TypeToken<T> elementType;

  /**
   * Factory method to create a PositionalInfo from a java.lang.reflect.Field.
   *
   * @param field The field must contain a Arg<List<?>>. The List<?> represents zero or more positional arguments.
   * @return a PositionalInfo
   */
  static PositionalInfo createFromField(Field field) {
    Preconditions.checkNotNull(field);
    Positional positional = field.getAnnotation(Positional.class);
    if (positional == null) {
      throw new Configuration.ConfigurationException(
          "No @Positional Arg annotation for field " + field);
    }

    Preconditions.checkArgument(
        TypeUtil.getRawType(TypeUtil.getTypeParam(field)) == List.class,
        "Field is annotated for positional parsing but is not of Arg<List<?>> type");
    Type nestedType = TypeToken.extractTypeToken(TypeUtil.getTypeParam(field));

    @SuppressWarnings("unchecked")
    PositionalInfo positionalInfo = new PositionalInfo(
        field.getDeclaringClass().getCanonicalName() + "." + field.getName(),
        positional.help(),
        ArgumentInfo.getArgForField(field),
        TypeUtil.getTypeParamTypeToken(field),
        TypeToken.create(nestedType),
        Arrays.asList(field.getAnnotations()),
        positional.parser());
    return positionalInfo;
  }

  public PositionalInfo(String canonicalName, String help, Arg<List<T>> arg,
      TypeToken<List<T>> type, TypeToken<T> elementType, List<Annotation> verifierAnnotations,
      @Nullable Class<? extends Parser<? extends List<T>>> parser) {
    super(help, arg, type, verifierAnnotations, parser);
    this.elementType = elementType;
    this.canonicalName = canonicalName;
  }

  /**
   * Get the "name" of the positional argument. Positional arguments, unlike optional arguments, don't
   * have names (like `-foo=bar`). However, when printing help info, we use the name to represent the positional
   * argument.
   *
   * @return the string "[positional args]"
   */
  @Override
  public String getName() {
    return "[positional args]";
  }

  String getCanonicalName() {
    return this.canonicalName;
  }

  void load(final ParserOracle parserOracle, List<String> positionalArgs) {
    final Parser<? extends T> parser = parserOracle.get(elementType);
    List<T> assignmentValue = Lists.newArrayList(Iterables.transform(positionalArgs,
      new Function<String, T>() {
        @Override public T apply(String argValue) {
          return parser.parse(parserOracle, elementType.getType(), argValue);
        }
      }));
    setValue(assignmentValue);
  }
}