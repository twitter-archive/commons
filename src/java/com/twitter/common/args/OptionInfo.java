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

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;

import com.twitter.common.args.apt.Configuration;
import com.twitter.common.base.Function;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Description of a command line option/flag such as -foo=bar.
 */
public final class OptionInfo<T> extends ArgumentInfo<T> {
  static final String ARG_NAME_RE = "[\\w\\-\\.]+";
  static final String ARG_FILE_HELP_TEMPLATE
      = "%s  Note this argument supports @argfile format where argfile is a file containing "
      + "otherwise cmdline argument value. For example: -%s=@/tmp/%s_value.txt. The format "
      + "of the argfile content should be exactly the same as it would be specified on the "
      + "cmdline.";
  private static final Pattern ARG_NAME_PATTERN = Pattern.compile(ARG_NAME_RE);
  private static final String NEGATE_BOOLEAN = "no_";
  private static final String ARG_FILE_INDICATOR = "@";

  /**
   * Factory method to create a OptionInfo from a field.
   *
   * @param field The field must contain a {@link Arg}.
   * @return an OptionInfo describing the field.
   */
  static OptionInfo<?> createFromField(Field field) {
    return createFromField(field, null);
  }

  /**
   * Factory method to create a OptionInfo from a field.
   *
   * @param field The field must contain a {@link Arg}.
   * @param instance The object containing the non-static Arg instance or else null if the Arg
   *     field is static.
   * @return an OptionInfo describing the field.
   */
  static OptionInfo<?> createFromField(final Field field, @Nullable Object instance) {
    CmdLine cmdLine = field.getAnnotation(CmdLine.class);
    if (cmdLine == null) {
      throw new Configuration.ConfigurationException(
          "No @CmdLine Arg annotation for field " + field);
    }

    String name = cmdLine.name();
    Preconditions.checkNotNull(name);
    checkArgument(!HELP_ARGS.contains(name),
        String.format("Argument name '%s' is reserved for builtin argument help", name));
    checkArgument(ARG_NAME_PATTERN.matcher(name).matches(),
        String.format("Argument name '%s' does not match required pattern %s",
            name, ARG_NAME_RE));

    Function<String, String> canonicalizer = new Function<String, String>() {
      @Override public String apply(String name) {
        return field.getDeclaringClass().getCanonicalName() + "." + name;
      }
    };

    @SuppressWarnings({"unchecked", "rawtypes"}) // we have no way to know the type here
    OptionInfo<?> optionInfo = new OptionInfo(
        canonicalizer,
        name,
        getCmdLineHelp(cmdLine),
        cmdLine.argFileAllowed(),
        ArgumentInfo.getArgForField(field, Optional.fromNullable(instance)),
        TypeUtil.getTypeParamTypeToken(field),
        Arrays.asList(field.getAnnotations()),
        cmdLine.parser());

    return optionInfo;
  }

  /**
   * Return the help string for a given cmdline argument. If @argfile format is
   * allowed, it will append additional help information about the @argfile usage.
   * @param cmdLine The cmdline argument.
   * @return The help string for the argument.
   */
  private static String getCmdLineHelp(CmdLine cmdLine) {
    String help = cmdLine.help();

    if (cmdLine.argFileAllowed()) {
      help = String.format(ARG_FILE_HELP_TEMPLATE, help, cmdLine.name(), cmdLine.name());
    }

    return help;
  }

  private final Function<String, String> canonicalizer;
  private final boolean argFileAllowed;

  private OptionInfo(
      Function<String, String> canonicalizer,
      String name,
      String help,
      boolean argFileAllowed,
      Arg<T> arg,
      TypeToken<T> type,
      List<Annotation> verifierAnnotations,
      @Nullable Class<? extends Parser<T>> parser) {

    super(canonicalizer.apply(name), name, help, arg, type,
        verifierAnnotations, parser);
    this.canonicalizer = canonicalizer;
    this.argFileAllowed = argFileAllowed;
  }

  /**
   * Parses the value and store result in the {@link Arg} contained in this {@code OptionInfo}.
   */
  void load(ParserOracle parserOracle, String optionName, String value) {
    Parser<? extends T> parser = getParser(parserOracle);

    String finalValue = value;

    // If "-arg=@file" is allowed and specified, then we read the value from the file
    // and use it as the raw value to be parsed for the argument.
    if (isArgFileAllowed()
        && !Strings.isNullOrEmpty(value)
        && value.startsWith(ARG_FILE_INDICATOR)) {
      finalValue = getArgFileContent(optionName, value.substring(ARG_FILE_INDICATOR.length()));
    }

    Object result = parser.parse(parserOracle, getType().getType(), finalValue); // [A]

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

  boolean isArgFileAllowed() { return argFileAllowed; }

  boolean isBoolean() {
    return getType().getRawType() == Boolean.class;
  }

  /**
   * Similar to the simple name, but with boolean arguments appends "no_", as in:
   * {@code -no_fire=false}
   */
  String getNegatedName() {
    return NEGATE_BOOLEAN + getName();
  }

  /**
   * Similar to the canonical name, but with boolean arguments appends "no_", as in:
   * {@code -com.twitter.common.MyApp.no_fire=false}
   */
  String getCanonicalNegatedName() {
    return canonicalizer.apply(getNegatedName());
  }

  /**
   * Read out the argument file content and return it as the argument value.
   * @param optionName The argument name
   * @param argFilePath The argument file path
   * @return The value of the argument file content.
   * @throws IllegalArgumentException if argFilePath is null or empty, or it fails to read
   *     the content of the file.
   */
  String getArgFileContent(String optionName, String argFilePath)
      throws IllegalArgumentException {
    if (Strings.isNullOrEmpty(argFilePath)) {
      throw new IllegalArgumentException(
          String.format("Invalid null/empty value for argument '%s'." + optionName));
    }

    try {
      return Files.toString(new File(argFilePath), Charsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("Unable to read argument '%s' value from file '%s'.",
              optionName, argFilePath),
          e);
    }
  }
}
