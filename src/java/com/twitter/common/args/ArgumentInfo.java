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
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

import com.twitter.common.args.constraints.NotNullVerifier;
import com.twitter.common.base.MorePreconditions;

/**
 * Description of a command line {@link Arg} instance.
 */
public abstract class ArgumentInfo<T> {
  static final ImmutableSet<String> HELP_ARGS = ImmutableSet.of("h", "help");

  /**
   * Extracts the {@code Arg} from the given field.
   *
   * @param field The field containing the {@code Arg}.
   * @param instance An optional object instance containing the field.
   * @return The extracted {@code} Arg.
   * @throws IllegalArgumentException If the field does not contain an arg.
   */
  protected static Arg<?> getArgForField(Field field, Optional<?> instance) {
    Preconditions.checkArgument(field.getType() == Arg.class,
        "Field is annotated for argument parsing but is not of Arg type: " + field);
    Preconditions.checkArgument(Modifier.isStatic(field.getModifiers()) || instance.isPresent(),
        "Non-static argument fields are not supported, found " + field);

    field.setAccessible(true);
    try {
      return (Arg<?>) field.get(instance.orNull());
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot get arg value for " + field);
    }
  }

  private final String canonicalName;
  private final String name;
  private final String help;
  private final boolean argFile;
  private final Arg<T> arg;
  private final TypeToken<T> type;
  private final List<Annotation> verifierAnnotations;
  @Nullable private final Class<? extends Parser<? extends T>> parser;

  /**
   * Creates a new {@code ArgsInfo}.
   *
   * @param canonicalName A fully qualified name for the argument.
   * @param name The simple name for the argument.
   * @param help Help string.
   * @param argFile If argument file is allowed.
   * @param arg Argument object.
   * @param type Concrete argument type.
   * @param verifierAnnotations {@link com.twitter.common.args.Verifier} annotations for this
   *     argument.
   * @param parser Parser for the argument type.
   */
  protected ArgumentInfo(
      String canonicalName,
      String name,
      String help,
      boolean argFile,
      Arg<T> arg,
      TypeToken<T> type,
      List<Annotation> verifierAnnotations,
      @Nullable Class<? extends Parser<? extends T>> parser) {

    this.canonicalName = MorePreconditions.checkNotBlank(canonicalName);
    this.name = MorePreconditions.checkNotBlank(name);
    this.help = MorePreconditions.checkNotBlank(help);
    this.argFile = argFile;
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
  public final String getName() {
    return name;
  }

  /**
   * Return the fully-qualified name of the command line argument. This is used as a command-line
   * optional argument, as in: -prefix.name=value. Prefix is typically a java package and class like
   * "com.twitter.myapp.MyClass". The difference between a canonical name and a regular name is that
   * it is in some circumstances for two names to collide; the canonical name, then, disambiguates.
   */
  public final String getCanonicalName() {
    return canonicalName;
  }

  /**
   * Returns the instructions for this command-line argument. This is typically used when the
   * executable is passed the -help flag.
   */
  public String getHelp() {
    return help;
  }

  /**
   * Returns whether an argument file is allowed for this argument.
   */
  public boolean argFile() {
    return argFile;
  }

  /**
   * Returns the Arg associated with this command-line argument. The Arg<?> is a mutable container
   * cell that holds the value passed-in on the command line, after parsing and validation.
   */
  public Arg<T> getArg() {
    return arg;
  }

  /**
   * Sets the value of the {@link Arg} described by this {@code ArgumentInfo}.
   *
   * @param value The value to set.
   */
  protected void setValue(@Nullable T value) {
    arg.set(value);
  }

  /**
   * Returns the TypeToken that represents the type of this command-line argument.
   */
  public TypeToken<T> getType() {
    return type;
  }

  @Override
  public boolean equals(Object object) {
    return (object instanceof ArgumentInfo) && arg.equals(((ArgumentInfo) object).arg);
  }

  @Override
  public int hashCode() {
    return arg.hashCode();
  }

  /**
   * Finds an appropriate parser for this args underlying value type.
   *
   * @param parserOracle The registry of known parsers.
   * @return A parser that can parse strings into the underlying argument type.
   * @throws IllegalArgumentException If no parser was found for the underlying argument type.
   */
  protected Parser<? extends T> getParser(ParserOracle parserOracle) {
    Preconditions.checkNotNull(parserOracle);
    if (parser == null || NoParser.class.equals(parser)) {
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

  static class ValueVerifier<T> {
    private final Verifier<? super T> verifier;
    private final Annotation annotation;

    ValueVerifier(Verifier<? super T> verifier, Annotation annotation) {
      this.verifier = verifier;
      this.annotation = annotation;
    }

    void verify(@Nullable T value) {
      if (value != null || verifier instanceof NotNullVerifier) {
        verifier.verify(value, annotation);
      }
    }

    String toString(Class<? extends T> rawType) {
      return verifier.toString(rawType, annotation);
    }
  }

  private Iterable<ValueVerifier<T>> getVerifiers(final Verifiers verifierOracle) {
    Function<Annotation, Optional<ValueVerifier<T>>> toVerifier =
        new Function<Annotation, Optional<ValueVerifier<T>>>() {
          @Override public Optional<ValueVerifier<T>> apply(Annotation annotation) {
            @Nullable Verifier<? super T> verifier = verifierOracle.get(type, annotation);
            if (verifier != null) {
              return Optional.of(new ValueVerifier<T>(verifier, annotation));
            } else {
              return Optional.absent();
            }
          }
        };
    return Optional.presentInstances(Iterables.transform(verifierAnnotations, toVerifier));
  }

  void verify(Verifiers verifierOracle) {
    @Nullable T value = getArg().uncheckedGet();
    for (ValueVerifier<T> valueVerifier : getVerifiers(verifierOracle)) {
      valueVerifier.verify(value);
    }
  }

  ImmutableList<String> collectConstraints(Verifiers verifierOracle) {
    @SuppressWarnings("unchecked") // type.getType() is T
    final Class<? extends T> rawType = (Class<? extends T>) type.getRawType();
    return FluentIterable.from(getVerifiers(verifierOracle)).transform(
        new Function<ValueVerifier<T>, String>() {
          @Override public String apply(ValueVerifier<T> verifier) {
            return verifier.toString(rawType);
          }
        }).toList();
  }
}
