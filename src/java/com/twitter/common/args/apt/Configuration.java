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

package com.twitter.common.args.apt;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.google.common.io.LineProcessor;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * Loads and stores {@literal @CmdLine} configuration data.
 *
 * @author John Sirois
 */
public class Configuration {

  /**
   * Indicates a problem reading stored {@literal @CmdLine} arg configuration data.
   */
  public static class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message) {
      super(message);
    }
    public ConfigurationException(Throwable cause) {
      super(cause);
    }
  }

  private static class FieldInfo {
    private static final CharMatcher IDENTIFIER_START =
        CharMatcher.forPredicate(new Predicate<Character>() {
          @Override public boolean apply(Character c) {
            return Character.isJavaIdentifierStart(c);
          }
        });

    private static final CharMatcher IDENTIFIER_REST =
        CharMatcher.forPredicate(new Predicate<Character>() {
          @Override public boolean apply(Character c) {
            return Character.isJavaIdentifierPart(c);
          }
        });

    private static String checkValidIdentifier(String identifier, boolean compound) {
      Preconditions.checkNotNull(identifier);

      String trimmed = identifier.trim();
      Preconditions.checkArgument(!trimmed.isEmpty(), "Invalid identifier: '%s'", identifier);

      String[] parts = compound ? trimmed.split("\\.") : new String[] { trimmed };
      for (String part : parts) {
        Preconditions.checkArgument(
            IDENTIFIER_REST.matchesAllOf(IDENTIFIER_START.trimLeadingFrom(part)),
            "Invalid identifier: '%s'", identifier);
      }

      return trimmed;
    }

    final String className;
    final String fieldName;

    FieldInfo(String className, String fieldName) {
      this.className = checkValidIdentifier(className, true);
      this.fieldName = checkValidIdentifier(fieldName, false);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (!(obj instanceof FieldInfo)) {
        return false;
      }

      FieldInfo other = (FieldInfo) obj;

      return new EqualsBuilder()
          .append(className, other.className)
          .append(fieldName, other.fieldName)
          .isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(className)
          .append(fieldName)
          .toHashCode();
    }
  }

  static class Builder {
    private final Set<FieldInfo> infos = Sets.newHashSet();

    public boolean isEmpty() {
      return infos.isEmpty();
    }

    public void addCmdLineArg(String className, String fieldName) {
      infos.add(new FieldInfo(className, fieldName));
    }

    public Configuration build() {
      return new Configuration(infos);
    }
  }

  private static final Function<URL, InputSupplier<? extends InputStream>> URL_TO_INPUT =
      new Function<URL, InputSupplier<? extends InputStream>>() {
        @Override public InputSupplier<? extends InputStream> apply(final URL resource) {
          return new InputSupplier<InputStream>() {
            @Override public InputStream getInput() throws IOException {
              return resource.openStream();
            }
          };
        }
      };

  private static final Function<InputSupplier<? extends InputStream>,
                                InputSupplier<? extends Reader>> INPUT_TO_READER =
      new Function<InputSupplier<? extends InputStream>, InputSupplier<? extends Reader>>() {
        @Override public InputSupplier<? extends Reader> apply(
            final InputSupplier<? extends InputStream> input) {
          return CharStreams.newReaderSupplier(input, Charsets.UTF_8);
        }
      };

  private static final Function<URL, InputSupplier<? extends Reader>> URL_TO_READER =
      Functions.compose(INPUT_TO_READER, URL_TO_INPUT);

  static final String DEFAULT_RESOURCE_PACKAGE =
      Configuration.class.getPackage().getName();
  static final String DEFAULT_RESOURCE_NAME = "cmdline.arg.fields.txt";
  static final String DEFAULT_RESOURCE_PATH =
      String.format("%s/%s", DEFAULT_RESOURCE_PACKAGE.replace('.', '/'), DEFAULT_RESOURCE_NAME);
  private static final String FIELD_NAME_SEPARATOR = " ";

  private static final Logger LOG = Logger.getLogger(Configuration.class.getName());

  /**
   * Loads the {@literal @CmdLine} argument configuration data stored in the classpath.
   *
   * @return The {@literal @CmdLine} argument configuration materialized from the classpath.
   * @throws ConfigurationException if any configuration data is malformed.
   * @throws IOException if the configuration data can not be read from the classpath.
   */
  public static Configuration load() throws ConfigurationException, IOException {
    List<URL> configs =
        ImmutableList.copyOf(Iterators.forEnumeration(
            Configuration.class.getClassLoader().getResources(DEFAULT_RESOURCE_PATH)));
    LOG.info("Loading @CmdLine config from :" + configs);
    return load(CharStreams.join(Iterables.transform(configs, URL_TO_READER)));
  }

  private static class ConfigurationParser implements LineProcessor<Configuration> {
    private final ImmutableList.Builder<FieldInfo> infoBuilder = ImmutableList.builder();

    int lineNumber = 0;

    @Override
    public boolean processLine(String line) throws IOException {
      ++lineNumber;
      String fieldId = line.trim();
      if (!fieldId.isEmpty() && !fieldId.startsWith("#")) {
        String[] parts = fieldId.split(FIELD_NAME_SEPARATOR);
        if (parts.length != 2) {
          throw new ConfigurationException("Invalid fieldId: " + fieldId + " @" + lineNumber);
        }
        infoBuilder.add(new FieldInfo(parts[0], parts[1]));
      }
      return true;
    }

    @Override
    public Configuration getResult() {
      return new Configuration(infoBuilder.build());
    }
  }

  private static Configuration load(InputSupplier<Reader> input)
      throws ConfigurationException, IOException {
    return CharStreams.readLines(input, new ConfigurationParser());
  }

  private static final Function<FieldInfo, Field> INFO_TO_FIELD =
      new Function<FieldInfo, Field>() {
        @Override public Field apply(FieldInfo info) {
          try {
            return Class.forName(info.className).getDeclaredField(info.fieldName);
          } catch (NoSuchFieldException e) {
            throw new ConfigurationException(e);
          } catch (ClassNotFoundException e) {
            throw new ConfigurationException(e);
          }
        }
      };

  private final ImmutableSet<FieldInfo> infos;

  private Configuration(Iterable<FieldInfo> infos) {
    this.infos = ImmutableSet.copyOf(infos);
  }

  /**
   * Returns all the {@literal @CmdLine} annotated fields on the classpath.
   *
   * @return All the {@literal @CmdLine} annotated fields.
   * @throws ConfigurationException if {@literal @CmdLine} annotated fields cannot be located.
   */
  public Iterable<Field> fields() throws ConfigurationException {
    return Iterables.transform(infos, INFO_TO_FIELD);
  }

  void store(Writer output, String message) {
    PrintWriter writer = new PrintWriter(output);
    writer.printf("# %s\n", new Date());
    writer.printf("# %s\n ", message);
    writer.println();
    for (FieldInfo info : infos) {
      writer.printf("%s%s%s\n", info.className, FIELD_NAME_SEPARATOR, info.fieldName);
    }
  }
}
