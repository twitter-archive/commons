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
import java.lang.reflect.Field;
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
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import com.twitter.common.args.Args.ArgsInfo;
import com.twitter.common.args.apt.Configuration;
import com.twitter.common.collections.Pair;

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
 */
public final class ArgScanner {

  private static final Function<OptionInfo<?>, String> GET_OPTION_INFO_NAME =
      new Function<OptionInfo<?>, String>() {
        @Override public String apply(OptionInfo<?> optionInfo) {
          return optionInfo.getName();
        }
      };

  public static final Ordering<OptionInfo<?>> ORDER_BY_NAME =
      Ordering.natural().onResultOf(GET_OPTION_INFO_NAME);

  private static final Function<String, String> ARG_NAME_TO_FLAG = new Function<String, String>() {
    @Override public String apply(String argName) {
      return "-" + argName;
    }
  };

  private static final Predicate<OptionInfo<?>> IS_BOOLEAN =
      new Predicate<OptionInfo<?>>() {
        @Override public boolean apply(OptionInfo<?> optionInfo) {
          return optionInfo.isBoolean();
        }
      };

  // Regular expression to identify a possible dangling assignment.
  // A dangling assignment occurs in two cases:
  //   - The command line used spaces between arg names and values, causing the name and value to
  //     end up in different command line arg array elements.
  //   - The command line is using the short form for a boolean argument,
  //     such as -use_feature, or -no_use_feature.
  private static final String DANGLING_ASSIGNMENT_RE =
      String.format("^-%s", OptionInfo.ARG_NAME_RE);
  private static final Pattern DANGLING_ASSIGNMENT_PATTERN =
      Pattern.compile(DANGLING_ASSIGNMENT_RE);

  // Pattern to identify a full assignment, which would be disassociated from a preceding dangling
  // assignment.
  private static final Pattern ASSIGNMENT_PATTERN =
      Pattern.compile(String.format("%s=.+", DANGLING_ASSIGNMENT_RE));

  /**
   * Extracts the name from an @OptionInfo.
   */
  private static final Function<OptionInfo<?>, String> GET_OPTION_INFO_NEGATED_NAME =
      new Function<OptionInfo<?>, String>() {
        @Override public String apply(OptionInfo<?> optionInfo) {
          return optionInfo.getNegatedName();
        }
      };

  /**
   * Gets the canonical name for an @Arg, based on the class containing the field it annotates.
   */
  private static final Function<OptionInfo<?>, String> GET_CANONICAL_ARG_NAME =
      new Function<OptionInfo<?>, String>() {
        @Override public String apply(OptionInfo<?> optionInfo) {
          return optionInfo.getCanonicalName();
        }
      };

  /**
   * Gets the canonical negated name for an @Arg.
   */
  private static final Function<OptionInfo<?>, String> GET_CANONICAL_NEGATED_ARG_NAME =
      new Function<OptionInfo<?>, String>() {
        @Override public String apply(OptionInfo<?> optionInfo) {
          return optionInfo.getCanonicalNegatedName();
        }
      };

  private static final Logger LOG = Logger.getLogger(ArgScanner.class.getName());

  // Pattern for the required argument format.
  private static final Pattern ARG_PATTERN =
      Pattern.compile(String.format("-(%s)(?:(?:=| +)(.*))?", OptionInfo.ARG_NAME_RE));

  private static final Pattern QUOTE_PATTERN = Pattern.compile("(['\"])([^\\\1]*)\\1");

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
    return parse(ArgFilters.SELECT_ALL, ImmutableList.copyOf(args));
  }

  /**
   * Applies the provided argument values to any {@literal @CmdLine} or {@literal @Positional}
   * {@code Arg} fields discovered on the classpath and accepted by the given {@code filter}.
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
    Preconditions.checkNotNull(filter);
    ImmutableList<String> arguments = ImmutableList.copyOf(args);

    Configuration configuration = load();
    ArgsInfo argsInfo = Args.fromConfiguration(configuration, filter);
    return parse(argsInfo, arguments);
  }

  /**
   * Parse command line arguments given a {@link ArgsInfo}
   *
   * @param argsInfo A description of any optional and positional arguments to parse.
   * @param args Argument values to map, parse, validate, and apply.
   * @return {@code true} if the given {@code args} were successfully applied to their corresponding
   *     {@link Arg} fields.
   * @throws ArgScanException if there was a problem loading {@literal @CmdLine} argument
   *    definitions
   * @throws IllegalArgumentException If the arguments provided are invalid based on the declared
   *    arguments found.
   */
  public boolean parse(ArgsInfo argsInfo, Iterable<String> args) {
    Preconditions.checkNotNull(argsInfo);
    ImmutableList<String> arguments = ImmutableList.copyOf(args);

    ParserOracle parserOracle = Parsers.fromConfiguration(argsInfo.getConfiguration());
    Verifiers verifiers = Verifiers.fromConfiguration(argsInfo.getConfiguration());
    Pair<ImmutableMap<String, String>, List<String>> results = mapArguments(arguments);
    return process(parserOracle, verifiers, argsInfo, results.getFirst(), results.getSecond());
  }

  private Configuration load() {
    try {
      return Configuration.load();
    } catch (IOException e) {
      throw new ArgScanException(e);
    }
  }

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

  private static <T> Set<T> dropCollisions(Iterable<T> input) {
    Set<T> copy = Sets.newHashSet();
    Set<T> collisions = Sets.newHashSet();
    for (T entry : input) {
      if (!copy.add(entry)) {
        collisions.add(entry);
      }
    }

    copy.removeAll(collisions);
    return copy;
  }

  private static Set<String> getNoCollisions(Iterable<? extends OptionInfo<?>> optionInfos) {
    Iterable<String> argShortNames = Iterables.transform(optionInfos, GET_OPTION_INFO_NAME);
    Iterable<String> argShortNegNames =
        Iterables.transform(Iterables.filter(optionInfos, IS_BOOLEAN),
            GET_OPTION_INFO_NEGATED_NAME);
    Iterable<String> argAllShortNames = Iterables.concat(argShortNames, argShortNegNames);
    Set<String> argAllShortNamesNoCollisions = dropCollisions(argAllShortNames);
    Set<String> collisionsDropped = Sets.difference(ImmutableSet.copyOf(argAllShortNames),
        argAllShortNamesNoCollisions);
    if (!collisionsDropped.isEmpty()) {
      LOG.warning("Found argument name collisions, args must be referenced by canonical names: "
          + collisionsDropped);
    }
    return argAllShortNamesNoCollisions;
  }

  /**
   * Applies argument values to fields based on their annotations.
   *
   * @param parserOracle ParserOracle available to parse raw args with.
   * @param verifiers Verifiers available to verify argument constraints with.
   * @param argsInfo Fields to apply argument values to.
   * @param args Unparsed argument values.
   * @param positionalArgs The unparsed positional arguments.
   * @return {@code true} if the given {@code args} were successfully applied to their
   *     corresponding {@link com.twitter.common.args.Arg} fields.
   */
  private boolean process(final ParserOracle parserOracle,
      Verifiers verifiers,
      ArgsInfo argsInfo,
      Map<String, String> args,
      List<String> positionalArgs) {

    if (!Sets.intersection(args.keySet(), ArgumentInfo.HELP_ARGS).isEmpty()) {
      printHelp(verifiers, argsInfo);
      return false;
    }

    Optional<? extends PositionalInfo<?>> positionalInfoOptional = argsInfo.getPositionalInfo();
    checkArgument(positionalInfoOptional.isPresent() || positionalArgs.isEmpty(),
        "Positional arguments have been supplied but there is no Arg annotated to received them.");

    Iterable<? extends OptionInfo<?>> optionInfos = argsInfo.getOptionInfos();

    final Set<String> argsFailedToParse = Sets.newHashSet();
    final Set<String> argsConstraintsFailed = Sets.newHashSet();

    Set<String> argAllShortNamesNoCollisions = getNoCollisions(optionInfos);

    final Map<String, OptionInfo<?>> argsByName =
        ImmutableMap.<String, OptionInfo<?>>builder()
        // Map by short arg name -> arg def.
        .putAll(Maps.uniqueIndex(Iterables.filter(optionInfos,
            Predicates.compose(Predicates.in(argAllShortNamesNoCollisions), GET_OPTION_INFO_NAME)),
            GET_OPTION_INFO_NAME))
        // Map by canonical arg name -> arg def.
        .putAll(Maps.uniqueIndex(optionInfos, GET_CANONICAL_ARG_NAME))
        // Map by negated short arg name (for booleans)
        .putAll(Maps.uniqueIndex(
            Iterables.filter(Iterables.filter(optionInfos, IS_BOOLEAN),
                Predicates.compose(Predicates.in(argAllShortNamesNoCollisions),
                    GET_OPTION_INFO_NEGATED_NAME)),
            GET_OPTION_INFO_NEGATED_NAME))
        // Map by negated canonical arg name (for booleans)
        .putAll(Maps.uniqueIndex(Iterables.filter(optionInfos, IS_BOOLEAN),
            GET_CANONICAL_NEGATED_ARG_NAME))
        .build();

    // TODO(William Farner): Make sure to disallow duplicate arg specification by short and
    // canonical names.

    // TODO(William Farner): Support non-atomic argument constraints.  @OnlyIfSet, @OnlyIfNotSet,
    //    @ExclusiveOf to define inter-argument constraints.

    Set<String> recognizedArgs = Sets.intersection(argsByName.keySet(), args.keySet());

    for (String argName : recognizedArgs) {
      String argValue = args.get(argName);
      OptionInfo<?> optionInfo = argsByName.get(argName);

      try {
        optionInfo.load(parserOracle, argName, argValue);
      } catch (IllegalArgumentException e) {
        argsFailedToParse.add(argName + " - " + e.getMessage());
      }
    }

    if (positionalInfoOptional.isPresent()) {
      PositionalInfo<?> positionalInfo = positionalInfoOptional.get();
      positionalInfo.load(parserOracle, positionalArgs);
    }

    Set<String> commandLineArgumentInfos = Sets.newTreeSet();

    Iterable<? extends ArgumentInfo<?>> allArguments = argsInfo.getOptionInfos();

    if (positionalInfoOptional.isPresent()) {
      PositionalInfo<?> positionalInfo = positionalInfoOptional.get();
      allArguments = Iterables.concat(optionInfos, ImmutableList.of(positionalInfo));
    }

    for (ArgumentInfo<?> anArgumentInfo : allArguments) {
      Arg<?> arg = anArgumentInfo.getArg();

      commandLineArgumentInfos.add(String.format("%s (%s): %s",
          anArgumentInfo.getName(), anArgumentInfo.getCanonicalName(),
          arg.uncheckedGet()));

      try {
        anArgumentInfo.verify(verifiers);
      } catch (IllegalArgumentException e) {
        argsConstraintsFailed.add(anArgumentInfo.getName() + " - " + e.getMessage());
      }
    }

    ImmutableMultimap<String, String> warningMessages =
        ImmutableMultimap.<String, String>builder()
        .putAll("Unrecognized arguments", Sets.difference(args.keySet(), argsByName.keySet()))
        .putAll("Failed to parse", argsFailedToParse)
        .putAll("Value did not meet constraints", argsConstraintsFailed)
        .build();

    if (!warningMessages.isEmpty()) {
      printHelp(verifiers, argsInfo);
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, Collection<String>> warnings : warningMessages.asMap().entrySet()) {
        sb.append(warnings.getKey()).append(":\n\t").append(Joiner.on("\n\t")
            .join(warnings.getValue())).append("\n");
      }
      throw new IllegalArgumentException(sb.toString());
    }

    LOG.info("-------------------------------------------------------------------------");
    LOG.info("Command line argument values");
    for (String commandLineArgumentInfo : commandLineArgumentInfos) {
      LOG.info(commandLineArgumentInfo);
    }
    LOG.info("-------------------------------------------------------------------------");
    return true;
  }

  private void printHelp(Verifiers verifiers, ArgsInfo argsInfo) {
    ImmutableList.Builder<String> requiredHelps = ImmutableList.builder();
    ImmutableList.Builder<String> optionalHelps = ImmutableList.builder();
    Optional<String> firstArgFileArgumentName = Optional.absent();
    for (OptionInfo<?> optionInfo
        : ORDER_BY_NAME.immutableSortedCopy(argsInfo.getOptionInfos())) {
      Arg<?> arg = optionInfo.getArg();
      Object defaultValue = arg.uncheckedGet();
      ImmutableList<String> constraints = optionInfo.collectConstraints(verifiers);
      String help = formatHelp(optionInfo, constraints, defaultValue);
      if (!arg.hasDefault()) {
        requiredHelps.add(help);
      } else {
        optionalHelps.add(help);
      }
      if (optionInfo.argFile() && !firstArgFileArgumentName.isPresent()) {
        firstArgFileArgumentName = Optional.of(optionInfo.getName());
      }
    }

    infoLog("-------------------------------------------------------------------------");
    infoLog(String.format("%s to print this help message",
        Joiner.on(" or ").join(Iterables.transform(ArgumentInfo.HELP_ARGS, ARG_NAME_TO_FLAG))));
    Optional<? extends PositionalInfo<?>> positionalInfoOptional = argsInfo.getPositionalInfo();
    if (positionalInfoOptional.isPresent()) {
      infoLog("\nPositional args:");
      PositionalInfo<?> positionalInfo = positionalInfoOptional.get();
      Arg<?> arg = positionalInfo.getArg();
      Object defaultValue = arg.uncheckedGet();
      ImmutableList<String> constraints = positionalInfo.collectConstraints(verifiers);
      infoLog(String.format("%s%s\n\t%s\n\t(%s)",
                            defaultValue != null ? "default " + defaultValue : "",
                            Iterables.isEmpty(constraints)
                                ? ""
                                : " [" + Joiner.on(", ").join(constraints) + "]",
                            positionalInfo.getHelp(),
                            positionalInfo.getCanonicalName()));
      // TODO: https://github.com/twitter/commons/issues/353, in the future we may
      // want to support @argfile format for positional arguments. We should check
      // to update firstArgFileArgumentName for them as well.
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
    if (firstArgFileArgumentName.isPresent()) {
      infoLog(String.format("\n"
          + "For arguments that support @argfile format: @argfile is a text file that contains "
          + "cmdline argument values. For example: -%s=@/tmp/%s_value.txt. The format "
          + "of the argfile content should be exactly the same as it would be specified on the "
          + "cmdline.", firstArgFileArgumentName.get(), firstArgFileArgumentName.get()));
    }
    infoLog("-------------------------------------------------------------------------");
  }

  private String formatHelp(ArgumentInfo<?> argumentInfo, Iterable<String> constraints,
                            @Nullable Object defaultValue) {

    return String.format("-%s%s%s\n\t%s\n\t(%s)",
                         argumentInfo.getName(),
                         defaultValue != null ? "=" + defaultValue : "",
                         Iterables.isEmpty(constraints)
                             ? ""
                             : " [" + Joiner.on(", ").join(constraints) + "]",
                         argumentInfo.getHelp(),
                         argumentInfo.getCanonicalName());
  }

  private void infoLog(String msg) {
    out.println(msg);
  }

  /**
   * Indicates a problem scanning {@literal @CmdLine} arg definitions.
   */
  public static class ArgScanException extends RuntimeException {
    public ArgScanException(Throwable cause) {
      super(cause);
    }
  }
}
