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
import java.lang.reflect.Modifier;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

import com.twitter.common.args.constraints.NotNullVerifier;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.collections.Pair;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Description of a command line argument.
 *
 * @author Nick Kallen
 */
public abstract class ArgumentInfo<T> {
  static final ImmutableSet<String> HELP_ARGS = ImmutableSet.of("h", "help");

  private final String help;
  private final Arg<T> arg;
  private final TypeToken<T> type;
  private final List<Annotation> verifierAnnotations;
  @Nullable private final Class<? extends Parser<? extends T>> parser;

  /**
   * Creates a new argumentinfo.
   *
   * @param help Help string.
   * @param arg Argument object.
   * @param type Concrete argument type.
   * @param verifierAnnotations Annotations if verifiers for this argument.
   * @param parser Parser for the argument type.
   */
  public ArgumentInfo(
      String help,
      Arg<T> arg,
      TypeToken<T> type,
      List<Annotation> verifierAnnotations,
      @Nullable Class<? extends Parser<? extends T>> parser) {

    this.help = MorePreconditions.checkNotBlank(help);
    this.arg = Preconditions.checkNotNull(arg);
    this.type = Preconditions.checkNotNull(type);
    this.verifierAnnotations = ImmutableList.copyOf(verifierAnnotations);
    this.parser = parser;
  }

  /**
   * Return the name of the command line argument. In an optional argument, this is expressed on
   * the command line by "-name=value"; whereas, for a positional argument, the name indicates
   * the type/function.
   */
  public abstract String getName();

  protected Parser<? extends T> getParser(ParserOracle parserOracle) {
    Preconditions.checkNotNull(parserOracle);
    if (parser == null || Parser.class.equals(parser)) {
      return parserOracle.get(type);
    } else {
      try {
        return parser.newInstance();
      } catch (InstantiationException e) {
        throw new RuntimeException("Failed to instantiate parser " + parser);
      } catch (IllegalAccessException e) {
        throw new RuntimeException("No access to instantiate parser " + parser);
      }
    }
  }

  private Iterable<Pair<? extends Verifier<? super T>, Annotation>> getVerifiers(
      final Verifiers verifierOracle) {
    Function<Annotation, Pair<? extends Verifier<? super T>, Annotation>> toVerifier =
        new Function<Annotation, Pair<? extends Verifier<? super T>, Annotation>>() {
          @Override public Pair<? extends Verifier<? super T>, Annotation> apply(
              Annotation annotation) {

            @Nullable Verifier<? super T> verifier = verifierOracle.get(type, annotation);
            return (verifier != null) ? Pair.of(verifier, annotation) : null;
          }
        };
    return Iterables.filter(Iterables.transform(verifierAnnotations, toVerifier),
        Predicates.<Pair<? extends Verifier<? super T>, Annotation>>notNull());
  }

  /**
   * Returns the instructions for this command-line argument. This is typically used when the
   * executable is passed the -help flag.
   */
  public String getHelp() {
    return help;
  }

  /**
   * Returns the Arg associated with this command-line argument. The Arg<?> is a mutable container
   * cell that holds the value passed-in on the command line, after parsing and validation.
   */
  public Arg<T> getArg() {
    return arg;
  }

  /**
   * Returns the TypeToken that represents the type of this command-line argument.
   */
  public TypeToken<T> getType() {
    return type;
  }

  protected void setValue(T value) {
    arg.set(value);
  }

  abstract String getCanonicalName();

  protected static Arg<?> getArgForField(Field field) {
    Preconditions.checkArgument(field.getType() == Arg.class,
      "Field is annotated for argument parsing but is not of Arg type: " + field);
    checkArgument(Modifier.isStatic(field.getModifiers()),
      "Non-static argument fields are not supported, found " + field);

    field.setAccessible(true);
    try {
      return (Arg<?>) field.get(null);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot get arg value for " + field);
    }
  }

  void verify(Verifiers verifierOracle) {
    T value = getArg().uncheckedGet();
    for (Pair<? extends Verifier<? super T>, Annotation> pair : getVerifiers(verifierOracle)) {
      Verifier<? super T> verifier = pair.getFirst();
      Annotation annotation = pair.getSecond();
      if (value != null || verifier instanceof NotNullVerifier) {
        verifier.verify(value, annotation);
      }
    }
  }

  void collectConstraints(Verifiers verifierOracle, ImmutableList.Builder<String> constraints) {
    for (Pair<? extends Verifier<? super T>, Annotation> pair : getVerifiers(verifierOracle)) {
      Verifier<? super T> verifier = pair.getFirst();
      Annotation annotation = pair.getSecond();

      @SuppressWarnings("unchecked") // type.getType() is T
      Class<? extends T> rawType = (Class<? extends T>) type.getRawType();

      String constraint = verifier.toString(rawType, annotation);
      constraints.add(constraint);
    }
  }
}
