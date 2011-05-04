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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
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
import com.google.common.collect.Sets;

import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.reflections.util.FilterBuilder.Include;

import com.twitter.common.args.Parsers.Parser;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.args.constraints.Verifier;
import com.twitter.common.base.Closure;
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
 * TODO(William Farner): Make default verifier and parser classes package-private and in this package.
 *
 * @author William Farner
 */
public class ArgScanner {

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

  private ArgScanner() {
    // Utility.
  }

  /**
   * Recursively scans a package prefix for declared arguments, and applies the provided argument
   * values, after parsing and validating.
   *
   * @param packagePrefix Prefix of package to scan.
   * @param args Argument values to parse, validate, and apply.
   * @throws IllegalArgumentException If the arguments provided are invalid based on the declared
   *    arguments found.
   */
  public static void parse(Iterable<String> packagePrefix, Map<String, String> args) {
    process(scan(packagePrefix), args);
  }

  /**
   * Convenience ethod to call {@link #parse(Iterable, Map)} that will handle mapping of argument
   * keys to values.
   *
   * @param packagePrefix Prefix of package to scan.
   * @param args Argument values to map, parse, validate, and apply.
   * @throws IllegalArgumentException If the arguments provided are invalid based on the declared
   *    arguments found.
   */
  public static void parse(Iterable<String> packagePrefix, String... args)
      throws IllegalArgumentException {
    parse(packagePrefix, mapArguments(args));
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

  @VisibleForTesting static List<String> joinKeysToValues(String... args) {
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
   * @return Map from argument key (arg name) to value.
   */
  public static Map<String, String> mapArguments(String... args) {
    ImmutableMap.Builder<String, String> argMap = ImmutableMap.builder();
    for (String arg : joinKeysToValues(args)) {
      Matcher matcher = ARG_PATTERN.matcher(arg);
      checkArgument(matcher.matches(),
          String.format("Argument '%s' does not match required format -arg_name=arg_value", arg));

      String rawValue = matcher.group(2);
      // An empty string denotes that the argument was passed with no value.
      rawValue = rawValue == null ? "" : stripQuotes(rawValue);
      argMap.put(matcher.group(1), rawValue);
    }

    return argMap.build();
  }

  /**
   * Extracts the @Arg annotation from a Field and includes it as a pair.
   */
  private static final Function<Field, Pair<Field, CmdLine>> PAIR_WITH_ANNOTATION =
      new Function<Field, Pair<Field, CmdLine>>() {
        @Override public Pair<Field, CmdLine> apply(Field field) {
          CmdLine cmdLineDef = field.getAnnotation(CmdLine.class);
          Preconditions.checkNotNull(cmdLineDef, "No Arg annotation for field " + field);
          return Pair.of(field, cmdLineDef);
        }
      };

  /**
   * Performs multiple checks to ensure the definition of an argument is good.
   */
  private static final Closure<Pair<Field, CmdLine>> CHECK_ARG_DEF =
      new Closure<Pair<Field, CmdLine>>() {
    @Override public void execute(Pair<Field, CmdLine> argDef) {
      checkArgument(Modifier.isStatic(argDef.getFirst().getModifiers()),
          "Non-static argument fields are not supported, found " + argDef.getFirst());

      String argName = argDef.getSecond().name();
      checkArgument(ARG_NAME_PATTERN.matcher(argName).matches(),
          String.format("Argument name '%s' on %s does not match required pattern %s",
              argName, argDef.getFirst(), ARG_NAME_RE));

      checkArgument(getParser(argDef.getSecond(), argDef.getFirst()) != null,
          "No parser found for type " + TypeUtil.getTypeParamClass(argDef.getFirst())
              + ", for arg field " + argDef.getFirst().getName());
    }
  };

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
  private static final Function<Pair<Field, CmdLine>, String> GET_CANONICAL_ARG_NAME =
      new Function<Pair<Field, CmdLine>, String>() {
        @Override public String apply(Pair<Field, CmdLine> argDef) {
          return argDef.getFirst().getDeclaringClass().getCanonicalName()
              + "." + argDef.getSecond().name();
        }
      };

  /**
   * Gets the canonical negated name for an @Arg.
   */
  private static final Function<Pair<Field, CmdLine>, String> GET_CANONICAL_NEGATED_ARG_NAME =
      new Function<Pair<Field, CmdLine>, String>() {
        @Override public String apply(Pair<Field, CmdLine> argDef) {
          return argDef.getFirst().getDeclaringClass().getCanonicalName()
              + "." + NEGATE_BOOLEAN + argDef.getSecond().name();
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

  /**
   * Applies argument values to fields based on their annotations.
   *
   * @param argFields Fields to apply argument values to.
   * @param args Unparsed argument values.
   */
  @VisibleForTesting
  static void process(Set<Field> argFields, Map<String, String> args) {

    final Set<String> argsFailedToParse = Sets.newHashSet();
    final Set<String> argsConstraintsFailed = Sets.newHashSet();

    final Iterable<Pair<Field, CmdLine>> argDefs =
        Iterables.transform(argFields, PAIR_WITH_ANNOTATION);

    for (Pair<Field, CmdLine> argDef : argDefs) {
      CHECK_ARG_DEF.execute(argDef);
    }

    Iterable<String> argShortNames = Iterables.transform(argDefs,
        Functions.compose(GET_ARG_NAME, Pair.<Field, CmdLine>second()));
    Set<String> argShortNamesNoCollisions = dropCollisions(argShortNames);
    Set<String> collisionsDropped = Sets.difference(ImmutableSet.copyOf(argShortNames),
        argShortNamesNoCollisions);
    if (!collisionsDropped.isEmpty()) {
      LOG.warning("Found argument name collisions, args must be referenced by canonical names: "
          + collisionsDropped);
    }

    Predicate<Pair<Field, CmdLine>> isBoolean = Predicates.compose(
        Predicates.<Class>equalTo(Boolean.class),
        Functions.compose(TypeUtil.GET_TYPE_PARAM_CLASS, Pair.<Field, CmdLine>first()));

    final Function<Pair<Field, CmdLine>, String> getFieldArgName =
        Functions.compose(GET_ARG_NAME, Pair.<Field, CmdLine>second());

    final Map<String, Pair<Field, CmdLine>> argsByName =
        ImmutableMap.<String, Pair<Field, CmdLine>>builder()
        // Map by short arg name -> arg def.
        .putAll(Maps.uniqueIndex(Iterables.filter(argDefs,
            Predicates.compose(Predicates.in(argShortNamesNoCollisions), getFieldArgName)),
            getFieldArgName))
        // Map by canonical arg name -> arg def.
        .putAll(Maps.uniqueIndex(argDefs, GET_CANONICAL_ARG_NAME))
        // Map by negated short arg name (for booleans)
        .putAll(Maps.uniqueIndex(Iterables.filter(argDefs, isBoolean),
            Functions.compose(PREPEND_NEGATION, getFieldArgName)))
        // Map by negated canonical arg name (for booleans)
        .putAll(Maps.uniqueIndex(Iterables.filter(argDefs, isBoolean),
            GET_CANONICAL_NEGATED_ARG_NAME))
        .build();

    // TODO(William Farner): Make sure to disallow duplicate arg specification by short and canonical
    //    names.

    // TODO(William Farner): Support non-atomic argument constraints.  @OnlyIfSet, @OnlyIfNotSet,
    //    @ExclusiveOf to define inter-argument constraints.

    Set<String> recognizedArgs = Sets.intersection(argsByName.keySet(), args.keySet());

    for (String argName : recognizedArgs) {
      String argValue = args.get(argName);
      Field argField = argsByName.get(argName).getFirst();
      CmdLine annotation = argsByName.get(argName).getSecond();

      Object assignmentValue;
      try {
        assignmentValue = getParser(annotation, argField)
            .parse(TypeUtil.getTypeParam(argField), argValue);
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

    Set<String> commandLineArgumentInfos = Sets.newTreeSet();
    for (Pair<Field, CmdLine> argDef : argDefs) {
      Field argField = argDef.getFirst();
      Arg arg = getArg(argField);

      commandLineArgumentInfos.add(String.format("%s (%s.%s): %s",
          argDef.getSecond().name(), argField.getDeclaringClass().getName(), argField.getName(),
          arg.uncheckedGet()));

      try {
        checkConstraints(TypeUtil.getTypeParamClass(argField), arg.uncheckedGet(),
            argField.getAnnotations());
      } catch (IllegalArgumentException e) {
        argsConstraintsFailed.add(argDef.getSecond().name() + " - " + e.getMessage());
      }
    }
    infoLog("-------------------------------------------------------------------------");
    infoLog("Command line argument values");
    for (String commandLineArgumentInfo : commandLineArgumentInfos) {
      infoLog(commandLineArgumentInfo);
    }
    infoLog("-------------------------------------------------------------------------");

    ImmutableMultimap<String, String> warningMessages =
        ImmutableMultimap.<String, String>builder()
        .putAll("Unrecognized arguments", Sets.difference(args.keySet(), argsByName.keySet()))
        .putAll("Failed to parse", argsFailedToParse)
        .putAll("Value did not meet constraints", argsConstraintsFailed)
        .build();

    if (!warningMessages.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, Collection<String>> warnings : warningMessages.asMap().entrySet()) {
        sb.append(warnings.getKey()).append(":\n\t").append(Joiner.on("\n\t")
            .join(warnings.getValue())).append("\n");
      }
      throw new IllegalArgumentException(sb.toString());
    }
  }

  private static void infoLog(String msg) {
    System.out.println(msg);
  }

  private static void checkConstraints(Class<?> fieldClass, Object value, Annotation[] annotations) {
    for (Annotation annotation : maybeFilterNullable(fieldClass, value, annotations)) {
      verify(fieldClass, value, annotation);
    }
  }

  private static Iterable<Annotation> maybeFilterNullable(Class<?> fieldClass, Object value,
      Annotation[] annotations) {

    // Apply all the normal constraint annotations
    if (value != null) {
      return Arrays.asList(annotations);
    }

    // The value is null so skip verifications unless a @NotNull was specified as a constraint
    for (Annotation annotation: annotations) {
      if (annotation instanceof NotNull) {
        verify(fieldClass, value, annotation); // will throw
      }
    }
    return ImmutableList.of();
  }

  @SuppressWarnings("unchecked")
  private static void verify(Class<?> fieldClass, Object value, Annotation annotation) {
    Verifier verifier = Constraints.get(fieldClass, annotation);
    if (verifier != null) {
      verifier.verify(value, annotation);
    }
  }

  private static Parser getParser(CmdLine cmdLine, Field argField) {
    Preconditions.checkArgument(argField.getType() == Arg.class,
        "Field is annotated with @CmdLine but is not of Arg type: " + argField);
    Class cls = TypeUtil.getTypeParamClass(argField);

    try {
      return cmdLine.parser() == Parser.class ? Parsers.get(cls) : cmdLine.parser().newInstance();
    } catch (InstantiationException e) {
      throw new RuntimeException("Failed to instantiate parser " + cmdLine.parser());
    } catch (IllegalAccessException e) {
      throw new RuntimeException("No access to instantiate parser " + cmdLine.parser());
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

  private static Set<Field> scan(Iterable<String> scanPackagePrefixes) {
    FilterBuilder filterBuilder = new FilterBuilder();
    ImmutableSet.Builder<URL> urlsBuilder = ImmutableSet.builder();
    for (String packagePrefix : scanPackagePrefixes) {
      filterBuilder.add(new FilterBuilder.Include(FilterBuilder.prefix(packagePrefix)));
      urlsBuilder.addAll(ClasspathHelper.getUrlsForPackagePrefix(packagePrefix));
    }
    filterBuilder.add(new FilterBuilder.Exclude(".*Test"));

    Set<URL> urls = urlsBuilder.build();
    LOG.info("Scanning resource URLs: " + urls);

    return new Reflections(new ConfigurationBuilder()
        .filterInputsBy(filterBuilder)
        .setUrls(urls)
        .useParallelExecutor()
        .setScanners(new FieldAnnotationsScanner()))
        .getFieldsAnnotatedWith(CmdLine.class);
  }
}
