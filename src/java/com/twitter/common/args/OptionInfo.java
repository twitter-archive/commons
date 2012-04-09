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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

import com.twitter.common.args.apt.Configuration;
import com.twitter.common.reflect.TypeToken;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Description of a command line option/flag such as -foo=bar.
 *
 * @author Nick Kallen
 */
public class OptionInfo<T> extends ArgumentInfo<T> {
  static final String ARG_NAME_RE = "[\\w\\-\\.]+";
  static final Pattern ARG_NAME_PATTERN = Pattern.compile(ARG_NAME_RE);
  static final String NEGATE_BOOLEAN = "no_";

  private final String name;
  private final String prefix;

  public OptionInfo(String name, String help, Arg<T> arg, TypeToken<T> type, String prefix,
      List<Annotation> verifierAnnotations, @Nullable Class<? extends Parser<? extends T>> parser) {
    super(help, arg, type, verifierAnnotations, parser);
    this.name = name;
    this.prefix = Preconditions.checkNotNull(prefix);
  }

  static OptionInfo createFromField(Field field) {
    Preconditions.checkNotNull(field);
    CmdLine cmdLine = field.getAnnotation(CmdLine.class);
    if (cmdLine == null) {
      throw new Configuration.ConfigurationException(
          "No @CmdLine Arg annotation for field " + field);
    }
    @SuppressWarnings("unchecked")
    OptionInfo optionInfo = new OptionInfo(
        checkValidName(cmdLine.name()),
        cmdLine.help(),
        ArgumentInfo.getArgForField(field),
        TypeUtil.getTypeParamTypeToken(field),
        field.getDeclaringClass().getCanonicalName(),
        Arrays.asList(field.getAnnotations()),
        cmdLine.parser());
    return optionInfo;
  }

  /**
   * Creates a new optioninfo.
   *
   * @param name Name of the option.
   * @param help Help string.
   * @param prefix Prefix scope for the option.
   * @param type Concrete option target type.
   * @param <T> Option type.
   * @return A new optioninfo.
   */
  public static <T> OptionInfo<T> create(String name, String help, String prefix, Class<T> type) {
    return new OptionInfo<T>(
        checkValidName(name),
        help,
        Arg.<T>create(),
        TypeToken.create(type),
        prefix,
        ImmutableList.<Annotation>of(),
        null);
  }

  /**
   * The name of the optional parameter. This is used as a command-line optional argument, as in:
   * -name=value.
   */
  public String getName() {
    return name;
  }

  String getNegatedName() {
    return NEGATE_BOOLEAN + this.name;
  }

  /**
   * Parse the value and store result in the Arg contained in this OptionInfo.
   */
  void load(ParserOracle parserOracle, String optionName, String value) {
    Parser<? extends T> parser = getParser(parserOracle);
    Object result = parser.parse(parserOracle, getType().getType(), value); // [A]

    // If the arg type is boolean, check if the command line uses the negated boolean form.
    if (isBoolean()) {
      if (Predicates.in(Arrays.asList(getNegatedName(), getCanonicalNegatedName()))
          .apply(optionName)) {
        result = !(Boolean) result; // [B]
      }
    }

    // We know result is T at line [A] but throw this type information away to allow negation if T
    // is Boolean at line [B]
    @SuppressWarnings("unchecked")
    T parsed = (T) result;

    setValue(parsed);
  }

  /**
   * A fully-qualified name of a parameter. This is used as a command-line optional argument, as in:
   * -prefix.name=value. Prefix is typically a java package and class like
   * "com.twitter.myapp.MyClass". The difference between a canonical name and a regular name is that
   * it is in some circumstances for two names to collide; the canonical name, then, disambiguates.
   */
  String getCanonicalName() {
    return this.prefix + "." + name;
  }

  boolean isBoolean() {
    return getType().getRawType() == Boolean.class;
  }

  /**
   * Similar to the canonical name, but with boolean arguments appends "no_", as in:
   * -com.twitter.common.MyApp.no_fire=false
   */
  String getCanonicalNegatedName() {
    return this.prefix + "." + NEGATE_BOOLEAN + this.name;
  }

  private static String checkValidName(String name) {
    Preconditions.checkNotNull(name);
    checkArgument(!HELP_ARGS.contains(name),
      String.format("Argument name '%s' is reserved for builtin argument help", name));
    checkArgument(ARG_NAME_PATTERN.matcher(name).matches(),
      String.format("Argument name '%s' does not match required pattern %s",
        name, ARG_NAME_RE));
    return name;
  }
}
