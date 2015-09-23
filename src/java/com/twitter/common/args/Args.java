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
import java.lang.reflect.Field;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import com.twitter.common.args.apt.Configuration;
import com.twitter.common.args.apt.Configuration.ArgInfo;

import static com.twitter.common.args.apt.Configuration.ConfigurationException;

/**
 * Utility that can load static {@literal @CmdLine} and {@literal @Positional} arg field info from
 * a configuration database or from explicitly listed containing classes or objects.
 */
public final class Args {
  @VisibleForTesting
  static final Function<ArgInfo, Optional<Field>> TO_FIELD =
      new Function<ArgInfo, Optional<Field>>() {
        @Override public Optional<Field> apply(ArgInfo info) {
          try {
            return Optional.of(Class.forName(info.className).getDeclaredField(info.fieldName));
          } catch (NoSuchFieldException e) {
            throw new ConfigurationException(e);
          } catch (ClassNotFoundException e) {
            throw new ConfigurationException(e);
          } catch (NoClassDefFoundError e) {
            // A compilation had this class available at the time the ArgInfo was deposited, but
            // the classes have been re-bundled with some subset including the class this ArgInfo
            // points to no longer available.  If the re-bundling is correct, then the arg truly is
            // not needed.
            LOG.fine(String.format("Not on current classpath, skipping %s", info));
            return Optional.absent();
          }
        }
      };

  private static final Logger LOG = Logger.getLogger(Args.class.getName());

  private static final Function<Field, OptionInfo<?>> TO_OPTION_INFO =
      new Function<Field, OptionInfo<?>>() {
        @Override public OptionInfo<?> apply(Field field) {
          @Nullable CmdLine cmdLine = field.getAnnotation(CmdLine.class);
          if (cmdLine == null) {
            throw new ConfigurationException("No @CmdLine Arg annotation for field " + field);
          }
          return OptionInfo.createFromField(field);
        }
      };

  private static final Function<Field, PositionalInfo<?>> TO_POSITIONAL_INFO =
      new Function<Field, PositionalInfo<?>>() {
        @Override public PositionalInfo<?> apply(Field field) {
          @Nullable Positional positional = field.getAnnotation(Positional.class);
          if (positional == null) {
            throw new ConfigurationException("No @Positional Arg annotation for field " + field);
          }
          return PositionalInfo.createFromField(field);
        }
      };

  /**
   * An opaque container for all the positional and optional {@link Arg} metadata in-play for a
   * command line parse.
   */
  public static final class ArgsInfo {
    private final Configuration configuration;
    private final Optional<? extends PositionalInfo<?>> positionalInfo;
    private final ImmutableList<? extends OptionInfo<?>> optionInfos;

    ArgsInfo(Configuration configuration,
             Optional<? extends PositionalInfo<?>> positionalInfo,
             Iterable<? extends OptionInfo<?>> optionInfos) {

      this.configuration = Preconditions.checkNotNull(configuration);
      this.positionalInfo = Preconditions.checkNotNull(positionalInfo);
      this.optionInfos = ImmutableList.copyOf(optionInfos);
    }

    Configuration getConfiguration() {
      return configuration;
    }

    Optional<? extends PositionalInfo<?>> getPositionalInfo() {
      return positionalInfo;
    }

    ImmutableList<? extends OptionInfo<?>> getOptionInfos() {
      return optionInfos;
    }
  }

  /**
   * Hydrates configured {@literal @CmdLine} arg fields and selects a desired set with the supplied
   * {@code filter}.
   *
   * @param configuration The configuration to find candidate {@literal @CmdLine} arg fields in.
   * @param filter A predicate to select fields with.
   * @return The desired hydrated {@literal @CmdLine} arg fields and optional {@literal @Positional}
   *     arg field.
   */
  static ArgsInfo fromConfiguration(Configuration configuration, Predicate<Field> filter) {
    ImmutableSet<Field> positionalFields =
        ImmutableSet.copyOf(filterFields(configuration.positionalInfo(), filter));

    if (positionalFields.size() > 1) {
      throw new IllegalArgumentException(
          String.format("Found %d fields marked for @Positional Args after applying filter - "
              + "only 1 is allowed:\n\t%s", positionalFields.size(),
              Joiner.on("\n\t").join(positionalFields)));
    }

    Optional<? extends PositionalInfo<?>> positionalInfo =
        Optional.fromNullable(
            Iterables.getOnlyElement(
                Iterables.transform(positionalFields, TO_POSITIONAL_INFO), null));

    Iterable<? extends OptionInfo<?>> optionInfos = Iterables.transform(
        filterFields(configuration.optionInfo(), filter), TO_OPTION_INFO);

    return new ArgsInfo(configuration, positionalInfo, optionInfos);
  }

  private static Iterable<Field> filterFields(Iterable<ArgInfo> infos, Predicate<Field> filter) {
    return Iterables.filter(
        Optional.presentInstances(Iterables.transform(infos, TO_FIELD)),
        filter);
  }

  /**
   * Equivalent to calling {@code from(Predicates.alwaysTrue(), Arrays.asList(sources)}.
   */
  public static ArgsInfo from(Object... sources) throws IOException {
    return from(ImmutableList.copyOf(sources));
  }

  /**
   * Equivalent to calling {@code from(filter, Arrays.asList(sources)}.
   */
  public static ArgsInfo from(Predicate<Field> filter, Object... sources) throws IOException {
    return from(filter, ImmutableList.copyOf(sources));
  }

  /**
   * Equivalent to calling {@code from(Predicates.alwaysTrue(), sources}.
   */
  public static ArgsInfo from(Iterable<?> sources) throws IOException {
    return from(Predicates.<Field>alwaysTrue(), sources);
  }

  /**
   * Loads arg info from the given sources in addition to the default compile-time configuration.
   *
   * @param filter A predicate to select fields with.
   * @param sources Classes or object instances to scan for {@link Arg} fields.
   * @return The args info describing all discovered {@link Arg args}.
   * @throws IOException If there was a problem loading the default Args configuration.
   */
  public static ArgsInfo from(Predicate<Field> filter, Iterable<?> sources) throws IOException {
    Preconditions.checkNotNull(filter);
    Preconditions.checkNotNull(sources);

    Configuration configuration = Configuration.load();
    ArgsInfo staticInfo = Args.fromConfiguration(configuration, filter);

    final ImmutableSet.Builder<PositionalInfo<?>> positionalInfos =
        ImmutableSet.<PositionalInfo<?>>builder().addAll(staticInfo.getPositionalInfo().asSet());
    final ImmutableSet.Builder<OptionInfo<?>> optionInfos =
        ImmutableSet.<OptionInfo<?>>builder().addAll(staticInfo.getOptionInfos());

    for (Object source : sources) {
      Class<?> clazz = source instanceof Class ? (Class) source : source.getClass();
      for (Field field : clazz.getDeclaredFields()) {
        if (filter.apply(field)) {
          boolean cmdLine = field.isAnnotationPresent(CmdLine.class);
          boolean positional = field.isAnnotationPresent(Positional.class);
          if (cmdLine && positional) {
            throw new IllegalArgumentException(
                "An Arg cannot be annotated with both @CmdLine and @Positional, found bad Arg "
                 + "field: " + field);
          } else if (cmdLine) {
            optionInfos.add(OptionInfo.createFromField(field, source));
          } else if (positional) {
            positionalInfos.add(PositionalInfo.createFromField(field, source));
          }
        }
      }
    }

    @Nullable PositionalInfo<?> positionalInfo =
        Iterables.getOnlyElement(positionalInfos.build(), null);
    return new ArgsInfo(configuration, Optional.fromNullable(positionalInfo), optionInfos.build());
  }

  private Args() {
    // utility
  }
}
