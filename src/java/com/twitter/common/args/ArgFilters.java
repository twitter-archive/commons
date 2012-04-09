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
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

import com.twitter.common.base.MorePreconditions;

/**
 * Utilities for generating {@literal @CmdLine} {@link Arg} filters suitable for use with
 * {@link com.twitter.common.args.ArgScanner#parse(Predicate, Iterable)}.  These filters assume the
 * fields parsed will all be annotated with {@link CmdLine}.
 *
 * @author John Sirois
 */
public final class ArgFilters {

  /**
   * A filter that selects all {@literal @CmdLine} {@link Arg}s found on the classpath.
   */
  public static final Predicate<Field> SELECT_ALL = Predicates.alwaysTrue();

  private ArgFilters() {
    // utility
  }

  /**
   * Creates a filter that selects all {@literal @CmdLine} {@link Arg}s found in classes that are
   * members of the given package.  Note that this will not select subpackages.
   *
   * @param pkg The exact package of classes whose command line args will be selected.
   * @return A filter that selects only command line args declared in classes that are members of
   *     the given {@code pkg}.
   */
  public static Predicate<Field> selectPackage(final Package pkg) {
    Preconditions.checkNotNull(pkg);
    return new Predicate<Field>() {
      @Override public boolean apply(Field field) {
        return field.getDeclaringClass().getPackage().equals(pkg);
      }
    };
  }

  /**
   * Creates a filter that selects all {@literal @CmdLine} {@link Arg}s found in classes that are
   * members of the given package or its sub-packages.
   *
   * @param pkg The ancestor package of classes whose command line args will be selected.
   * @return A filter that selects only command line args declared in classes that are members of
   *     the given {@code pkg} or its sub-packages.
   */
  public static Predicate<Field> selectAllPackagesUnderHere(final Package pkg) {
    Preconditions.checkNotNull(pkg);
    final String prefix = pkg.getName() + '.';
    return Predicates.or(selectPackage(pkg), new Predicate<Field>() {
      @Override public boolean apply(Field field) {
        return field.getDeclaringClass().getPackage().getName().startsWith(prefix);
      }
    });
  }

  /**
   * Creates a filter that selects all {@literal @CmdLine} {@link Arg}s found in the given class.
   *
   * @param clazz The class whose command line args will be selected.
   * @return A filter that selects only command line args declared in the given {@code clazz}.
   */
  public static Predicate<Field> selectClass(final Class<?> clazz) {
    Preconditions.checkNotNull(clazz);
    return new Predicate<Field>() {
      @Override public boolean apply(Field field) {
        return field.getDeclaringClass().equals(clazz);
      }
    };
  }

  /**
   * Creates a filter that selects all {@literal @CmdLine} {@link Arg}s found in the given classes.
   *
   * @param cls The classes whose command line args will be selected.
   * @return A filter that selects only command line args declared in the given classes.
   */
  public static Predicate<Field> selectClasses(final Class<?> ... cls) {
    Preconditions.checkNotNull(cls);
    final Set<Class<?>> listOfClasses = ImmutableSet.copyOf(cls);
    return new Predicate<Field>() {
      @Override public boolean apply(Field field) {
        return listOfClasses.contains(field.getDeclaringClass());
      }
    };
  }

  /**
   * Creates a filter that selects a single {@literal @CmdLine} {@link Arg}.
   *
   * @param clazz The class that declares the command line arg to be selected.
   * @param name The {@link com.twitter.common.args.CmdLine#name()} of the arg to select.
   * @return A filter that selects a single specified command line arg.
   */
  public static Predicate<Field> selectCmdLineArg(Class<?> clazz, final String name) {
    MorePreconditions.checkNotBlank(name);
    return Predicates.and(selectClass(clazz), new Predicate<Field>() {
      @Override public boolean apply(Field field) {
        return field.getAnnotation(CmdLine.class).name().equals(name);
      }
    });
  }
}
