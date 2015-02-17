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
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;

import com.twitter.common.args.apt.Configuration;

/**
 * Description of a positional command line argument.
 */
public final class PositionalInfo<T> extends ArgumentInfo<List<T>> {
  /**
   * Factory method to create a PositionalInfo from a field.
   *
   * @param field The field must contain a {@link Arg Arg&lt;List&lt;?&gt;&gt;}. The List&lt;?&gt;
   *     represents zero or more positional arguments.
   * @return a PositionalInfo describing the field.
   */
  static PositionalInfo<?> createFromField(Field field) {
    return createFromField(field, null);
  }

  /**
   * Factory method to create a PositionalInfo from a field.
   *
   * @param field The field must contain a {@link Arg Arg&lt;List&lt;?&gt;&gt;}. The List&lt;?&gt;
   *     represents zero or more positional arguments.
   * @param instance The object containing the non-static Arg instance or else null if the Arg
   *     field is static.
   * @return a PositionalInfo describing the field.
   */
  static PositionalInfo<?> createFromField(Field field, @Nullable Object instance) {
    Preconditions.checkNotNull(field);
    Positional positional = field.getAnnotation(Positional.class);
    if (positional == null) {
      throw new Configuration.ConfigurationException(
          "No @Positional Arg annotation for field " + field);
    }

    Preconditions.checkArgument(
        TypeUtil.getRawType(TypeUtil.getTypeParam(field)) == List.class,
        "Field is annotated for positional parsing but is not of Arg<List<?>> type");
    Type nestedType = TypeUtil.extractTypeToken(TypeUtil.getTypeParam(field));

    @SuppressWarnings({"unchecked", "rawtypes"}) // we have no way to know the type here
    PositionalInfo<?> positionalInfo = new PositionalInfo(
        field.getDeclaringClass().getCanonicalName() + "." + field.getName(),
        "[positional args]",
        positional.help(),
        ArgumentInfo.getArgForField(field, Optional.fromNullable(instance)),
        TypeUtil.getTypeParamTypeToken(field),
        TypeToken.of(nestedType),
        Arrays.asList(field.getAnnotations()),
        positional.parser());

    return positionalInfo;
  }

  private final TypeToken<T> elementType;

  private PositionalInfo(
      String canonicalName,
      String name,
      String help,
      Arg<List<T>> arg,
      TypeToken<List<T>> type,
      TypeToken<T> elementType,
      List<Annotation> verifierAnnotations,
      @Nullable Class<? extends Parser<? extends List<T>>> parser) {

    // TODO: https://github.com/twitter/commons/issues/353, consider future support of
    // argFile for Positional arguments.
    super(canonicalName, name, help, false, arg, type, verifierAnnotations, parser);
    this.elementType = elementType;
  }

  /**
   * Parses the positional args and stores the results in the {@link Arg} described by this
   * {@code PositionalInfo}.
   */
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
