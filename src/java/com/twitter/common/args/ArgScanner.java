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

import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import com.twitter.common.args.Args.ArgumentInfo;
import com.twitter.common.args.Args.OptionInfo;
import com.twitter.common.args.Args.PositionalInfo;
import com.twitter.common.args.apt.Configuration;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.collections.Pair;
import com.twitter.common.reflect.TypeToken;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Argument scanning, parsing, and validating system.  This class is designed recursively scan a
 * package for declared arguments, parse the values based on the declared type, and validate against
 * any constraints that the arugment is decorated with.
 *
 * The supported argument formats are:
 *   -arg_name=arg_value
 *   -arg_name arg_value
 * Where {@code arg_value} may be single or double-quoted if desired or necessary to prevent
 * splitting by the terminal application.
 *
 * A special format for boolean arguments is also supported.  The following syntaxes all set the
 * {@code bool_arg} to {@code true}:
 *   -bool_arg
 *   -bool_arg=true
 *   -no_bool_arg=false (double negation)
 *
 * Likewise, the following would set {@code bool_arg} to {@code false}:
 *   -no_bool_arg
 *   -bool_arg=false
 *   -no_bool_arg=true (negation)
 *
 * As with the general argument format, spaces may be used in place of equals for boolean argument
 * assignment.
 *
 * TODO(William Farner): Make default verifier and parser classes package-private and in this
 * package.
 *
 * @author William Farner
 */
public final class ArgScanner {

  private static final Function<OptionInfo, CmdLine> GET_CMD_LINE =
      new Function<OptionInfo, CmdLine>() {
        @Override public CmdLine apply(OptionInfo optionInfo) {
          return optionInfo.cmdLine;
        }
      };

  private static final Function<OptionInfo, Field> GET_FIELD =
      new Function<OptionInfo, Field>() {
        @Override public Field apply(OptionInfo optionInfo) {
          return optionInfo.field;
        }
      };

  /**
   * Indicates a problem scanning {@literal @CmdLine} arg definitions.
   */
  public static class ArgScanException extends RuntimeException {
    public ArgScanException(Throwable cause) {
      super(cause);
    }
  }

  private static final Logger LOG = Logger.getLogger(ArgScanner.class.getName());

  // Negation prefix for booleans.
  private static final String NEGATE_BOOLEAN = "no_";

  // Regular expression that defines the name format for an argument.
  private static final String ARG_NAME_RE = "[\\w\\-\\.]+";
  private static final Pattern ARG_NAME_PATTERN = Pattern.compile(ARG_NAME_RE);
  private static final Pattern NEGATED_BOOLEAN_PATTERN = Pattern.compile(
      String.format("(%s)?%s(%s)", ARG_NAME_RE, NEGATE_BOOLEAN, ARG_NAME_RE));

  // Pattern for the required argument format.
  private static final Pattern ARG_PATTERN =
      Pattern.compile(String.format("-(%s)(?:(?:=| +)(.*))?", ARG_NAME_RE));

  private final PrintStream out;

  /**
   * Equivalent to calling {@link #ArgScanner(PrintStream)} passing {@link System#out}.
   */
  public ArgScanner() {
    this(System.out);
  }

  /**
   * Creates a new ArgScanner that prints help on arg parse failure or when help is requested to
   * {@code out} or else prints applied argument information to {@code out} when parsing is
   * successful.
   *
   * @param out An output stream to write help and parsed argument info to.
   */
  public ArgScanner(PrintStream out) {
    this.out = Preconditions.checkNotNull(out);
  }

  /**
   * Applies the provided argument values to all {@literal @CmdLine} {@code Arg} fields discovered
   * on the classpath.
   *
   * @param args Argument values to map, parse, validate, and apply.
   * @return {@code true} if the given {@code args} were successfully applied to their corresponding
   *     {@link Arg} fields.
   * @throws ArgScanException if there was a problem loading {@literal @CmdLine} argument
   *    definitions
   * @throws IllegalArgumentException If the arguments provided are invalid based on the declared
   *    arguments found.
   */
  public boolean parse(Iterable<String> args) {
    return parse(ArgFilters.SELECT_ALL, args);
  }

  /**
   * Applies the provided argument values to any {@literal @CmdLine} {@code Arg} fields discovered
   * on the classpath and accepted by the given {@code filter}.
   *
   * @param filter A predicate that selects or rejects scanned {@literal @CmdLine} fields for
   *    argument application.
   * @param args Argument values to map, parse, validate, and apply.
   * @return {@code true} if the given {@code args} were successfully applied to their corresponding
   *     {@link Arg} fields.
   * @throws ArgScanException if there was a problem loading {@literal @CmdLine} argument
   *    definitions
   * @throws IllegalArgumentException If the arguments provided are invalid based on the declared
   *    arguments found.
   */
  public boolean parse(Predicate<Field> filter, Iterable<String> args) {
    Configuration configuration = load();
    ArgumentInfo argumentInfo = Args.fromConfiguration(configuration, filter);
    ParserOracle parserOracle = Parsers.fromConfiguration(configuration);
    Verifiers verifiers = Verifiers.fromConfiguration(configuration);
    Pair<ImmutableMap<String, String>, List<String>> results = mapArguments(args);
    return process(parserOracle, verifiers, argumentInfo, results.getFirst(), results.getSecond());
  }

  private Configuration load() {
    try {
      return Configuration.load();
    } catch (IOException e) {
      throw new ArgScanException(e);
    }
  }

  // Regular expression to identify a possible dangling assignment.
  // A dangling assignment occurs in two cases:
  //   - The command line used spaces between arg names and values, causing the name and value to
  //     end up in different command line arg array elements.
  //   - The command line is using the short form for a boolean argument,
  //     such as -use_feature, or -no_use_feature.
  private static final String DANGLING_ASSIGNMENT_RE = String.format("^-%s", ARG_NAME_RE);
  private static final Pattern DANGLING_ASSIGNMENT_PATTERN =
      Pattern.compile(DANGLING_ASSIGNMENT_RE);

  // Pattern to identify a full assignment, which would be disassociated from a preceding dangling
  // assignment.
  private static final Pattern ASSIGNMENT_PATTERN =
      Pattern.compile(String.format("%s=.+", DANGLING_ASSIGNMENT_RE));

  @VisibleForTesting static List<String> joinKeysToValues(Iterable<String> args) {
    List<String> joinedArgs = Lists.newArrayList();
    String unmappedKey = null;
    for (String arg : args) {
      if (unmappedKey == null) {
        if (DANGLING_ASSIGNMENT_PATTERN.matcher(arg).matches()) {
          // Beginning of a possible dangling assignment.
          unmappedKey = arg;
        } else {
          joinedArgs.add(arg);
        }
      } else {
        if (ASSIGNMENT_PATTERN.matcher(arg).matches()) {
          // Full assignment, disassociate from dangling assignment.
          joinedArgs.add(unmappedKey);
          joinedArgs.add(arg);
          unmappedKey = null;
        } else if (DANGLING_ASSIGNMENT_PATTERN.matcher(arg).find()) {
          // Another dangling assignment, this could be two sequential boolean args.
          joinedArgs.add(unmappedKey);
          unmappedKey = arg;
        } else {
          // Join the dangling key with its value.
          joinedArgs.add(unmappedKey + "=" + arg);
          unmappedKey = null;
        }
      }
    }

    if (unmappedKey != null) {
      joinedArgs.add(unmappedKey);
    }

    return joinedArgs;
  }

  private static final Pattern QUOTE_PATTERN = Pattern.compile("(['\"])([^\\\1]*)\\1");

  private static String stripQuotes(String str) {
    Matcher matcher = QUOTE_PATTERN.matcher(str);
    return matcher.matches() ? matcher.group(2) : str;
  }

  /**
   * Scans through args, mapping keys to values even if the arg values are 'dangling' and reside
   * in different array entries than the respective keys.
   *
   * @param args Arguments to build into a map.
   * @return A map from argument key (arg name) to value paired with a list of any leftover
   *     positional arguments.
   */
  private static Pair<ImmutableMap<String, String>, List<String>> mapArguments(
      Iterable<String> args) {

    ImmutableMap.Builder<String, String> argMap = ImmutableMap.builder();
    List<String> positionalArgs = Lists.newArrayList();
    for (String arg : joinKeysToValues(args)) {
      if (!arg.startsWith("-")) {
        positionalArgs.add(arg);
      } else {
        Matcher matcher = ARG_PATTERN.matcher(arg);
        checkArgument(matcher.matches(),
            String.format("Argument '%s' does not match required format -arg_name=arg_value", arg));

        String rawValue = matcher.group(2);
        // An empty string denotes that the argument was passed with no value.
        rawValue = rawValue == null ? "" : stripQuotes(rawValue);
        argMap.put(matcher.group(1), rawValue);
      }
    }

    return Pair.of(argMap.build(), positionalArgs);
  }

  private static final ImmutableSet<String> HELP_ARGS = ImmutableSet.of("h", "help");

  // TODO(John Sirois): Support these checks at compile time.
  private static void checkArgDef(ParserOracle parserOracle, Field field, CmdLine cmdLine) {
    checkArgDef(parserOracle, field, cmdLine.parser());

    String argName = cmdLine.name();
    checkArgument(!HELP_ARGS.contains(argName),
        String.format("Argument name '%s' is reserved for builtin argument help", argName));
    checkArgument(ARG_NAME_PATTERN.matcher(argName).matches(),
        String.format("Argument name '%s' on %s does not match required pattern %s",
            argName, field, ARG_NAME_RE));
  }

  // TODO(John Sirois): Support these checks at compile time.
  private static void checkArgDef(ParserOracle parserOracle, Field field,
      Class<? extends Parser> custom) {

    checkArgument(Modifier.isStatic(field.getModifiers()),
        "Non-static argument fields are not supported, found " + field);

    checkArgument(getParser(parserOracle, custom, field) != null,
        "No parser found for type " + TypeUtil.getTypeParamClass(field)
            + ", for arg field " + field.getName());
  }

  /**
   * Extracts the name from an @Arg.
   */
  private static final Function<CmdLine, String> GET_ARG_NAME = new Function<CmdLine, String>() {
      @Override public String apply(CmdLine cmdLine) {
        return cmdLine.name();
      }
    };

  /**
   * Gets the canonical name for an @Arg, based on the class containing the field it annotates.
   */
  private static final Function<OptionInfo, String> GET_CANONICAL_ARG_NAME =
      new Function<OptionInfo, String>() {
        @Override public String apply(OptionInfo optionInfo) {
          return optionInfo.field.getDeclaringClass().getCanonicalName()
              + "." + optionInfo.cmdLine.name();
        }
      };

  /**
   * Gets the canonical negated name for an @Arg.
   */
  private static final Function<OptionInfo, String> GET_CANONICAL_NEGATED_ARG_NAME =
      new Function<OptionInfo, String>() {
        @Override public String apply(OptionInfo optionInfo) {
          return optionInfo.field.getDeclaringClass().getCanonicalName()
              + "." + NEGATE_BOOLEAN + optionInfo.cmdLine.name();
        }
      };

  private static final Function<String, String> PREPEND_NEGATION = new Function<String, String>() {
    @Override public String apply(String name) {
      return NEGATE_BOOLEAN + name;
    }
  };

  private static <T> Set<T> dropCollisions(Iterable<T> input) {
    Set<T> copy = Sets.newHashSet();
    Set<T> collisions = Sets.newHashSet();
    for (T entry: input) {
      if (!copy.add(entry)) {
        collisions.add(entry);
      }
    }

    copy.removeAll(collisions);
    return copy;
  }

  private static final Function<OptionInfo, String> GET_FIELD_ARG_NAME =
      Functions.compose(GET_ARG_NAME, GET_CMD_LINE);

  /**
   * Applies argument values to fields based on their annotations.
   *
   * @param parserOracle ParserOracle available to parse raw args with.
   * @param verifiers Verifiers available to verify argument constraints with.
   * @param argumentInfo Fields to apply argument values to.
   * @param args Unparsed argument values.
   * @param positionalArgs The unparsed positional arguments.
   * @return {@code true} if the given {@code args} were successfully applied to their
   *     corresponding {@link com.twitter.common.args.Arg} fields.
   */
  private boolean process(final ParserOracle parserOracle, Verifiers verifiers,
      ArgumentInfo argumentInfo, Map<String, String> args,
      List<String> positionalArgs) {

    if (!Sets.intersection(args.keySet(), HELP_ARGS).isEmpty()) {
      printHelp(verifiers, argumentInfo);
      return false;
    }

    Optional<PositionalInfo> positionalInfo = argumentInfo.positionalInfo;
    checkArgument(positionalInfo.isPresent() || positionalArgs.isEmpty(),
        "Positional arguments have been supplied but there is no Arg annotated to received them.");
    if (positionalInfo.isPresent()) {
      PositionalInfo info = positionalInfo.get();
      checkArgDef(parserOracle, info.field, info.positional.parser());
    }

    Iterable<OptionInfo> optionInfos = argumentInfo.optionInfos;
    for (OptionInfo optionInfo : optionInfos) {
      checkArgDef(parserOracle, optionInfo.field, optionInfo.cmdLine);
    }

    final Set<String> argsFailedToParse = Sets.newHashSet();
    final Set<String> argsConstraintsFailed = Sets.newHashSet();

    Iterable<String> argShortNames = Iterables.transform(optionInfos,
        Functions.compose(GET_ARG_NAME, GET_CMD_LINE));
    Set<String> argShortNamesNoCollisions = dropCollisions(argShortNames);
    Set<String> collisionsDropped = Sets.difference(ImmutableSet.copyOf(argShortNames),
        argShortNamesNoCollisions);
    if (!collisionsDropped.isEmpty()) {
      LOG.warning("Found argument name collisions, args must be referenced by canonical names: "
          + collisionsDropped);
    }

    Predicate<OptionInfo> isBoolean = Predicates.compose(
        Predicates.<Class>equalTo(Boolean.class),
        Functions.compose(TypeUtil.GET_TYPE_PARAM_CLASS, GET_FIELD));

    final Map<String, OptionInfo> argsByName =
        ImmutableMap.<String, OptionInfo>builder()
        // Map by short arg name -> arg def.
        .putAll(Maps.uniqueIndex(Iterables.filter(optionInfos,
            Predicates.compose(Predicates.in(argShortNamesNoCollisions), GET_FIELD_ARG_NAME)),
                               GET_FIELD_ARG_NAME))
        // Map by canonical arg name -> arg def.
        .putAll(Maps.uniqueIndex(optionInfos, GET_CANONICAL_ARG_NAME))
        // Map by negated short arg name (for booleans)
        .putAll(Maps.uniqueIndex(Iterables.filter(optionInfos, isBoolean),
            Functions.compose(PREPEND_NEGATION, GET_FIELD_ARG_NAME)))
        // Map by negated canonical arg name (for booleans)
        .putAll(Maps.uniqueIndex(Iterables.filter(optionInfos, isBoolean),
            GET_CANONICAL_NEGATED_ARG_NAME))
        .build();

    // TODO(William Farner): Make sure to disallow duplicate arg specification by short and
    // canonical names.

    // TODO(William Farner): Support non-atomic argument constraints.  @OnlyIfSet, @OnlyIfNotSet,
    //    @ExclusiveOf to define inter-argument constraints.

    Set<String> recognizedArgs = Sets.intersection(argsByName.keySet(), args.keySet());

    for (String argName : recognizedArgs) {
      String argValue = args.get(argName);
      OptionInfo optionInfo = argsByName.get(argName);
      Field argField = optionInfo.field;
      Class<? extends Parser> parser = optionInfo.cmdLine.parser();

      Object assignmentValue;
      try {
        assignmentValue =
            getParser(parserOracle, parser, argField)
                .parse(parserOracle, TypeUtil.getTypeParam(argField), argValue);
      } catch (IllegalArgumentException e) {
        argsFailedToParse.add(argName + " - " + e.getMessage());
        continue;
      }

      // If the arg type is boolean, check if the command line uses the negated boolean form.
      if (TypeUtil.getTypeParamClass(argField) == Boolean.class) {
        Matcher negatedNameMatcher = NEGATED_BOOLEAN_PATTERN.matcher(argName);
        if (negatedNameMatcher.matches()) {
          String argShortName = negatedNameMatcher.group(negatedNameMatcher.groupCount());
          String argCanonicalName = negatedNameMatcher.groupCount() == 2 ?
              negatedNameMatcher.group(1) + argShortName : null;
          if (Iterables.any(argsByName.keySet(),
              Predicates.in(Arrays.asList(argShortName, argCanonicalName)))) {
            assignmentValue = (!(Boolean) assignmentValue);
          }
        }
      }

      setField(argField, assignmentValue);
    }

    if (positionalInfo.isPresent()) {
      PositionalInfo info = positionalInfo.get();
      final Field argField = info.field;
      Class<? extends Parser> custom = info.positional.parser();

      // We're trying to extract T 2 levels down in: Arg<List<T>>
      final Type elementType = TypeToken.extractTypeToken(TypeUtil.getTypeParam(argField));

      final Parser<?> parser = getParser(parserOracle, custom, TypeUtil.getRawType(elementType));
      List<?> assignmentValue = Lists.newArrayList(Iterables.transform(positionalArgs,
          new Function<String, Object>() {
            @Override public Object apply(String argValue) {
              return parser.parse(parserOracle, elementType, argValue);
            }
          }));

      setField(argField, assignmentValue);
    }

    Set<String> commandLineArgumentInfos = Sets.newTreeSet();

    Iterable<Pair<String, Field>> argFields = Iterables.transform(optionInfos,
        new Function<OptionInfo, Pair<String, Field>>() {
          @Override public Pair<String, Field> apply(OptionInfo optionInfo) {
            return Pair.of(optionInfo.cmdLine.name(), optionInfo.field);
          }
        });
    if (positionalInfo.isPresent()){
      PositionalInfo info = positionalInfo.get();
      argFields = Iterables.concat(argFields,
          ImmutableList.of(Pair.of("[positional args]", info.field)));
    }

    for (Pair<String, Field> argInfo : argFields) {
      Field argField = argInfo.getSecond();
      Arg arg = getArg(argField);

      commandLineArgumentInfos.add(String.format("%s (%s.%s): %s",
          argInfo.getFirst(), argField.getDeclaringClass().getName(), argField.getName(),
          arg.uncheckedGet()));

      try {
        checkConstraints(verifiers, TypeUtil.getTypeParamClass(argField), arg.uncheckedGet(),
            argField.getAnnotations());
      } catch (IllegalArgumentException e) {
        argsConstraintsFailed.add(argInfo.getFirst() + " - " + e.getMessage());
      }
    }

    ImmutableMultimap<String, String> warningMessages =
        ImmutableMultimap.<String, String>builder()
        .putAll("Unrecognized arguments", Sets.difference(args.keySet(), argsByName.keySet()))
        .putAll("Failed to parse", argsFailedToParse)
        .putAll("Value did not meet constraints", argsConstraintsFailed)
        .build();

    if (!warningMessages.isEmpty()) {
      printHelp(verifiers, argumentInfo);
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, Collection<String>> warnings : warningMessages.asMap().entrySet()) {
        sb.append(warnings.getKey()).append(":\n\t").append(Joiner.on("\n\t")
            .join(warnings.getValue())).append("\n");
      }
      throw new IllegalArgumentException(sb.toString());
    }

    infoLog("-------------------------------------------------------------------------");
    infoLog("Command line argument values");
    for (String commandLineArgumentInfo : commandLineArgumentInfos) {
      infoLog(commandLineArgumentInfo);
    }
    infoLog("-------------------------------------------------------------------------");
    return true;
  }

  private static final Function<String,String> ARG_NAME_TO_FLAG = new Function<String, String>() {
    @Override public String apply(String argName) {
      return "-" + argName;
    }
  };

  public static final Ordering<OptionInfo> ORDER_BY_NAME =
      Ordering.natural().onResultOf(GET_FIELD_ARG_NAME);

  private void printHelp(Verifiers verifiers, ArgumentInfo argumentInfo) {
    ImmutableList.Builder<String> requiredHelps = ImmutableList.builder();
    ImmutableList.Builder<String> optionalHelps = ImmutableList.builder();
    for (OptionInfo optionInfo : ORDER_BY_NAME.immutableSortedCopy(argumentInfo.optionInfos)) {
      Field field = optionInfo.field;
      CmdLine cmdLine = optionInfo.cmdLine;
      Arg arg = getArg(field);
      Object defaultValue = arg.uncheckedGet();
      ImmutableList<String> constraints = collectConstraints(verifiers, field);
      String help = formatHelp(cmdLine, field, constraints, defaultValue);
      if (!arg.hasDefault()) {
        requiredHelps.add(help);
      } else {
        optionalHelps.add(help);
      }
    }

    infoLog("-------------------------------------------------------------------------");
    infoLog(String.format("%s to print this help message",
        Joiner.on(" or ").join(Iterables.transform(HELP_ARGS, ARG_NAME_TO_FLAG))));
    Optional<PositionalInfo> positionalInfo = argumentInfo.positionalInfo;
    if (positionalInfo.isPresent()) {
      infoLog("\nPositional args:");
      PositionalInfo info = positionalInfo.get();
      Field field = info.field;
      Arg arg = getArg(field);
      Object defaultValue = arg.uncheckedGet();
      ImmutableList<String> constraints = collectConstraints(verifiers, field);
      infoLog(String.format("%s%s\n\t%s\n\t(%s.%s)",
                            defaultValue != null ? "default " + defaultValue : "",
                            Iterables.isEmpty(constraints)
                                ? ""
                                : " [" + Joiner.on(", ").join(constraints) + "]",
                            info.positional.help(),
                            field.getDeclaringClass().getName(),
                            field.getName()));
    }
    ImmutableList<String> required = requiredHelps.build();
    if (!required.isEmpty()) {
      infoLog("\nRequired flags:"); // yes - this should actually throw!
      infoLog(Joiner.on('\n').join(required));
    }
    ImmutableList<String> optional = optionalHelps.build();
    if (!optional.isEmpty()) {
      infoLog("\nOptional flags:");
      infoLog(Joiner.on('\n').join(optional));
    }
    infoLog("-------------------------------------------------------------------------");
  }

  private ImmutableList<String> collectConstraints(Verifiers verifiers, Field field) {
    Builder<String> constraints = ImmutableList.builder();
    for (Annotation annotation : field.getAnnotations()) {
      Class<?> argType = TypeUtil.getTypeParamClass(field);
      @SuppressWarnings("unchecked")
      Verifier verifier = verifiers.get(argType, annotation);
      if (verifier != null) {
        @SuppressWarnings("unchecked")
        String constraint = verifier.toString(argType, annotation);
        constraints.add(constraint);
      }
    }
    return constraints.build();
  }

  private String formatHelp(CmdLine cmdLine, Field field, Iterable<String> constraints,
                            @Nullable Object defaultValue) {

    return String.format("-%s%s%s\n\t%s\n\t(%s.%s)",
                         cmdLine.name(),
                         defaultValue != null ? "=" + defaultValue : "",
                         Iterables.isEmpty(constraints)
                             ? ""
                             : " [" + Joiner.on(", ").join(constraints) + "]",
                         cmdLine.help(),
                         field.getDeclaringClass().getName(),
                         field.getName());
  }

  private void infoLog(String msg) {
    out.println(msg);
  }

  private void checkConstraints(Verifiers verifiers, Class<?> fieldClass, Object value,
      Annotation[] annotations) {
    for (Annotation annotation : maybeFilterNullable(verifiers, fieldClass, value, annotations)) {
      verify(verifiers, fieldClass, value, annotation);
    }
  }

  private Iterable<Annotation> maybeFilterNullable(Verifiers verifiers, Class<?> fieldClass,
      Object value, Annotation[] annotations) {

    // Apply all the normal constraint annotations
    if (value != null) {
      return Arrays.asList(annotations);
    }

    // The value is null so skip verifications unless a @NotNull was specified as a constraint
    for (Annotation annotation: annotations) {
      if (annotation instanceof NotNull) {
        verify(verifiers, fieldClass, value, annotation); // will throw
      }
    }
    return ImmutableList.of();
  }

  @SuppressWarnings("unchecked")
  private void verify(Verifiers verifiers, Class<?> fieldClass, Object value,
      Annotation annotation) {
    Verifier verifier = verifiers.get(fieldClass, annotation);
    if (verifier != null) {
      verifier.verify(value, annotation);
    }
  }

  private static Parser<?> getParser(ParserOracle parserOracle, Class<? extends Parser> custom,
      Field argField) {

    Preconditions.checkArgument(argField.getType() == Arg.class,
        "Field is annotated for argument parsing but is not of Arg type: " + argField);
    return getParser(parserOracle, custom, TypeUtil.getTypeParamClass(argField));
  }

  private static Parser<?> getParser(ParserOracle parserOracle, Class<? extends Parser> custom,
      Class<?> cls) {
    try {
      return (custom == Parser.class) ? parserOracle.get(cls) : custom.newInstance();
    } catch (InstantiationException e) {
      throw new RuntimeException("Failed to instantiate parser " + custom);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("No access to instantiate parser " + custom);
    }
  }

  @SuppressWarnings("unchecked")
  private static void setField(Field field, Object value) {
    getArg(field).set(value);
  }

  private static Arg getArg(Field field) {
    field.setAccessible(true);
    try {
      return ((Arg) field.get(null));
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to set value for " + field);
    }
  }
}
