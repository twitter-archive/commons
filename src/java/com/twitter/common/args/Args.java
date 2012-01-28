package com.twitter.common.args;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import com.twitter.common.args.apt.Configuration;
import com.twitter.common.args.apt.Configuration.ArgInfo;

import static com.twitter.common.args.apt.Configuration.ConfigurationException;

/**
 * A utility that can load {@literal @CmdLine} arg field info from a configuration database.
 */
final class Args {
  private static final Logger LOG = Logger.getLogger(Args.class.getName());

  @VisibleForTesting
  public static final Function<ArgInfo, Optional<Field>> TO_FIELD =
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

  private static final Function<Field, OptionInfo> TO_OPTIONINFO =
      new Function<Field, OptionInfo>() {
        @Override public OptionInfo apply(Field field) {
          CmdLine cmdLineDef = field.getAnnotation(CmdLine.class);
          if (cmdLineDef == null) {
            throw new ConfigurationException("No @CmdLine Arg annotation for field " + field);
          }
          return new OptionInfo(cmdLineDef, field);
        }
      };

  private static final Function<Field, PositionalInfo> TO_POSITIONALINFO =
      new Function<Field, PositionalInfo>() {
        @Override public PositionalInfo apply(Field field) {
          Positional positional = field.getAnnotation(Positional.class);
          if (positional == null) {
            throw new ConfigurationException("No @Positional Arg annotation for field " + field);
          }
          return new PositionalInfo(positional, field);
        }
      };

  private Args() {
    // utility
  }

  static class OptionInfo {
    final CmdLine cmdLine;
    final Field field;

    OptionInfo(CmdLine cmdLine, Field field) {
      this.cmdLine = Preconditions.checkNotNull(cmdLine);
      this.field = Preconditions.checkNotNull(field);
    }
  }

  static class PositionalInfo {
    final Positional positional;
    final Field field;

    PositionalInfo(Positional positional, Field field) {
      this.positional = Preconditions.checkNotNull(positional);
      this.field = Preconditions.checkNotNull(field);
    }
  }

  static class ArgumentInfo {
    final Optional<PositionalInfo> positionalInfo;
    final Iterable<OptionInfo> optionInfos;

    ArgumentInfo(Optional<PositionalInfo> positionalInfo, Iterable<OptionInfo> optionInfos) {
      this.positionalInfo = Preconditions.checkNotNull(positionalInfo);
      this.optionInfos = Preconditions.checkNotNull(optionInfos);
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
  static ArgumentInfo fromConfiguration(Configuration configuration,
      Predicate<Field> filter) {

    ImmutableSet<Field> positionalFields =
        ImmutableSet.copyOf(filterFields(configuration.positionalInfo(), filter));

    if (positionalFields.size() > 1) {
      throw new IllegalArgumentException(
          String.format("Found %d fields marked for @Positional Args after applying filter - " +
              "only 1 is allowed:\n\t%s", positionalFields.size(),
              Joiner.on("\n\t").join(positionalFields)));
    }

    Optional<PositionalInfo> positionalInfo = Optional.fromNullable(
        Iterables.getOnlyElement(Iterables.transform(positionalFields, TO_POSITIONALINFO), null));
    Iterable<OptionInfo> optionInfos = Iterables.transform(
        filterFields(configuration.optionInfo(), filter), TO_OPTIONINFO);
    return new ArgumentInfo(positionalInfo, optionInfos);
  }

  private static Iterable<Field> filterFields(Iterable<ArgInfo> infos, Predicate<Field> filter) {
    return Iterables.filter(
        Optional.presentInstances(Iterables.transform(infos, TO_FIELD)),
        filter);
  }
}
