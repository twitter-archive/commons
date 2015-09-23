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
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.google.common.io.LineProcessor;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * Loads and stores {@literal @CmdLine} configuration data. By default, that data
 * is contained in text files called cmdline.arg.info.txt.0, cmdline.arg.info.txt.1
 * etc. Every time a new Configuration object is created, it consumes all existing
 * files with the above names. Saving this Configuration results in creation of a
 * file with index increased by one, e.g. cmdline.arg.info.txt.2 in the above
 * example.
 *
 * @author John Sirois
 */
public final class Configuration {

  /**
   * Indicates a problem reading stored {@literal @CmdLine} arg configuration data.
   */
  public static class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message, Object... args) {
      super(String.format(message, args));
    }
    public ConfigurationException(Throwable cause) {
      super(cause);
    }
  }

  static final String DEFAULT_RESOURCE_PACKAGE = Configuration.class.getPackage().getName();

  private static final Logger LOG = Logger.getLogger(Configuration.class.getName());

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

  private static final String DEFAULT_RESOURCE_NAME = "cmdline.arg.info.txt";

  private int nextResourceIndex;
  private final ImmutableSet<ArgInfo> positionalInfos;
  private final ImmutableSet<ArgInfo> cmdLineInfos;
  private final ImmutableSet<ParserInfo> parserInfos;
  private final ImmutableSet<VerifierInfo> verifierInfos;

  private Configuration(int nextResourceIndex,
      Iterable<ArgInfo> positionalInfos, Iterable<ArgInfo> cmdLineInfos,
      Iterable<ParserInfo> parserInfos, Iterable<VerifierInfo> verifierInfos) {
    this.nextResourceIndex = nextResourceIndex;
    this.positionalInfos = ImmutableSet.copyOf(positionalInfos);
    this.cmdLineInfos = ImmutableSet.copyOf(cmdLineInfos);
    this.parserInfos = ImmutableSet.copyOf(parserInfos);
    this.verifierInfos = ImmutableSet.copyOf(verifierInfos);
  }

  private static String checkValidIdentifier(String identifier, boolean compound) {
    Preconditions.checkNotNull(identifier);

    String trimmed = identifier.trim();
    Preconditions.checkArgument(!trimmed.isEmpty(), "Invalid identifier: '%s'", identifier);

    String[] parts = compound ? trimmed.split("\\.") : new String[] {trimmed};
    for (String part : parts) {
      Preconditions.checkArgument(
          IDENTIFIER_REST.matchesAllOf(IDENTIFIER_START.trimLeadingFrom(part)),
          "Invalid identifier: '%s'", identifier);
    }

    return trimmed;
  }

  public static final class ArgInfo {
    public final String className;
    public final String fieldName;

    public ArgInfo(String className, String fieldName) {
      this.className = checkValidIdentifier(className, true);
      this.fieldName = checkValidIdentifier(fieldName, false);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (!(obj instanceof ArgInfo)) {
        return false;
      }

      ArgInfo other = (ArgInfo) obj;

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

    @Override public String toString() {
      return new ToStringBuilder(this)
          .append("className", className)
          .append("fieldName", fieldName)
          .toString();
    }
  }

  public static final class ParserInfo {
    public final String parsedType;
    public final String parserClass;

    public ParserInfo(String parsedType, String parserClass) {
      this.parsedType = checkValidIdentifier(parsedType, true);
      this.parserClass = checkValidIdentifier(parserClass, true);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (!(obj instanceof ParserInfo)) {
        return false;
      }

      ParserInfo other = (ParserInfo) obj;

      return new EqualsBuilder()
          .append(parsedType, other.parsedType)
          .append(parserClass, other.parserClass)
          .isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(parsedType)
          .append(parserClass)
          .toHashCode();
    }

    @Override public String toString() {
      return new ToStringBuilder(this)
          .append("parsedType", parsedType)
          .append("parserClass", parserClass)
          .toString();
    }
  }

  public static final class VerifierInfo {
    public final String verifiedType;
    public final String verifyingAnnotation;
    public final String verifierClass;

    public VerifierInfo(String verifiedType, String verifyingAnnotation, String verifierClass) {
      this.verifiedType = checkValidIdentifier(verifiedType, true);
      this.verifyingAnnotation = checkValidIdentifier(verifyingAnnotation, true);
      this.verifierClass = checkValidIdentifier(verifierClass, true);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (!(obj instanceof VerifierInfo)) {
        return false;
      }

      VerifierInfo other = (VerifierInfo) obj;

      return new EqualsBuilder()
          .append(verifiedType, other.verifiedType)
          .append(verifyingAnnotation, other.verifyingAnnotation)
          .append(verifierClass, other.verifierClass)
          .isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(verifiedType)
          .append(verifyingAnnotation)
          .append(verifierClass)
          .toHashCode();
    }

    @Override public String toString() {
      return new ToStringBuilder(this)
          .append("verifiedType", verifiedType)
          .append("verifyingAnnotation", verifyingAnnotation)
          .append("verifierClass", verifierClass)
          .toString();
    }
  }

  static class Builder {
    private final Set<ArgInfo> positionalInfos = Sets.newHashSet();
    private final Set<ArgInfo> argInfos = Sets.newHashSet();
    private final Set<ParserInfo> parserInfos = Sets.newHashSet();
    private final Set<VerifierInfo> verifierInfos = Sets.newHashSet();

    public boolean isEmpty() {
      return positionalInfos.isEmpty()
          && argInfos.isEmpty()
          && parserInfos.isEmpty()
          && verifierInfos.isEmpty();
    }

    void addPositionalInfo(ArgInfo positionalInfo) {
      positionalInfos.add(positionalInfo);
    }

    void addCmdLineArg(ArgInfo argInfo) {
      argInfos.add(argInfo);
    }

    void addParser(ParserInfo parserInfo) {
      parserInfos.add(parserInfo);
    }

    public void addParser(String parserForType, String parserType) {
      addParser(new ParserInfo(parserForType, parserType));
    }

    void addVerifier(VerifierInfo verifierInfo) {
      verifierInfos.add(verifierInfo);
    }

    public void addVerifier(String verifierForType, String annotationType, String verifierType) {
      addVerifier(new VerifierInfo(verifierForType, annotationType, verifierType));
    }

    public Configuration build(Configuration configuration) {
      return new Configuration(configuration.nextResourceIndex + 1,
          positionalInfos, argInfos, parserInfos, verifierInfos);
    }
  }

  private static String getResourceName(int index) {
    return String.format("%s.%s", DEFAULT_RESOURCE_NAME, index);
  }

  private static String getResourcePath(int index) {
    return String.format("%s/%s", DEFAULT_RESOURCE_PACKAGE.replace('.', '/'),
        getResourceName(index));
  }

  static final class ConfigurationResources {
    private final int nextResourceIndex;
    private final Iterator<URL> resources;

    private ConfigurationResources(int nextResourceIndex, Iterator<URL> resources) {
      this.nextResourceIndex = nextResourceIndex;
      this.resources = resources;
    }
  }

  /**
   * Loads the {@literal @CmdLine} argument configuration data stored in the classpath.
   *
   * @return The {@literal @CmdLine} argument configuration materialized from the classpath.
   * @throws ConfigurationException if any configuration data is malformed.
   * @throws IOException if the configuration data can not be read from the classpath.
   */
  public static Configuration load() throws ConfigurationException, IOException {
    ConfigurationResources allResources = getAllResources();
    List<URL> configs = ImmutableList.copyOf(allResources.resources);
    if (configs.isEmpty()) {
      LOG.info("No @CmdLine arg configs found on the classpath");
    } else {
      LOG.info("Loading @CmdLine config from: " + configs);
    }
    return load(allResources.nextResourceIndex, configs);
  }

  private static ConfigurationResources getAllResources() throws IOException {
    int maxResourceIndex = 0;
    Iterator<URL> allResources = getResources(0); // Try for a main
    // Probe for resource files with index up to 10 (or more, while resources at the
    // given index can be found)
    for (int nextResourceIndex = 1; nextResourceIndex <= maxResourceIndex + 10;
         nextResourceIndex++) {
      Iterator<URL> resources = getResources(nextResourceIndex);
      if (resources.hasNext()) {
        allResources = Iterators.concat(allResources, resources);
        maxResourceIndex = nextResourceIndex;
      }
    }
    return new ConfigurationResources(maxResourceIndex + 1, allResources);
  }

  private static Iterator<URL> getResources(int index) throws IOException {
    return Iterators.forEnumeration(
        Configuration.class.getClassLoader().getResources(getResourcePath(index)));
  }

  private static final class ConfigurationParser implements LineProcessor<Configuration> {
    private final int nextIndex;
    private int lineNumber = 0;

    private final ImmutableList.Builder<ArgInfo> positionalInfo = ImmutableList.builder();
    private final ImmutableList.Builder<ArgInfo> fieldInfoBuilder = ImmutableList.builder();
    private final ImmutableList.Builder<ParserInfo> parserInfoBuilder = ImmutableList.builder();
    private final ImmutableList.Builder<VerifierInfo> verifierInfoBuilder = ImmutableList.builder();

    private ConfigurationParser(int nextIndex) {
      this.nextIndex = nextIndex;
    }

    @Override
    public boolean processLine(String line) throws IOException {
      ++lineNumber;
      String trimmed = line.trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
        List<String> parts = Lists.newArrayList(trimmed.split(" "));
        if (parts.size() < 1) {
          throw new ConfigurationException("Invalid line: %s @%d", trimmed, lineNumber);
        }

        String type = parts.remove(0);
        if ("positional".equals(type)) {
          if (parts.size() != 2) {
            throw new ConfigurationException(
                "Invalid positional line: %s @%d", trimmed, lineNumber);
          }
          positionalInfo.add(new ArgInfo(parts.get(0), parts.get(1)));
        } else if ("field".equals(type)) {
          if (parts.size() != 2) {
            throw new ConfigurationException("Invalid field line: %s @%d", trimmed, lineNumber);
          }
          fieldInfoBuilder.add(new ArgInfo(parts.get(0), parts.get(1)));
        } else if ("parser".equals(type)) {
          if (parts.size() != 2) {
            throw new ConfigurationException("Invalid parser line: %s @%d", trimmed, lineNumber);
          }
          parserInfoBuilder.add(new ParserInfo(parts.get(0), parts.get(1)));
        } else if ("verifier".equals(type)) {
          if (parts.size() != 3) {
            throw new ConfigurationException("Invalid verifier line: %s @%d", trimmed, lineNumber);
          }
          verifierInfoBuilder.add(new VerifierInfo(parts.get(0), parts.get(1), parts.get(2)));
        } else {
          LOG.warning(String.format("Did not recognize entry type %s for line: %s @%d",
              type, trimmed, lineNumber));
        }
      }
      return true;
    }

    @Override
    public Configuration getResult() {
      return new Configuration(nextIndex, positionalInfo.build(),
          fieldInfoBuilder.build(), parserInfoBuilder.build(), verifierInfoBuilder.build());
    }
  }

  private static Configuration load(int nextIndex, List<URL> configs)
      throws ConfigurationException, IOException {
    InputSupplier<Reader> input = CharStreams.join(Iterables.transform(configs, URL_TO_READER));
    return CharStreams.readLines(input, new ConfigurationParser(nextIndex));
  }

  public boolean isEmpty() {
    return positionalInfos.isEmpty()
        && cmdLineInfos.isEmpty()
        && parserInfos.isEmpty()
        && verifierInfos.isEmpty();
  }

  /**
   * Returns the field info for the sole {@literal @Positional} annotated field on the classpath,
   * if any.
   *
   * @return The field info for the {@literal @Positional} annotated field if any.
   */
  public Iterable<ArgInfo> positionalInfo() {
    return positionalInfos;
  }

  /**
   * Returns the field info for all the {@literal @CmdLine} annotated fields on the classpath.
   *
   * @return The field info for all the {@literal @CmdLine} annotated fields.
   */
  public Iterable<ArgInfo> optionInfo() {
    return cmdLineInfos;
  }

  /**
   * Returns the parser info for all the {@literal @ArgParser} annotated parsers on the classpath.
   *
   * @return The parser info for all the {@literal @ArgParser} annotated parsers.
   */
  public Iterable<ParserInfo> parserInfo() {
    return parserInfos;
  }

  /**
   * Returns the verifier info for all the {@literal @VerifierFor} annotated verifiers on the
   * classpath.
   *
   * @return The verifier info for all the {@literal @VerifierFor} annotated verifiers.
   */
  public Iterable<VerifierInfo> verifierInfo() {
    return verifierInfos;
  }

  static String mainResourceName() {
    return getResourceName(0);
  }

  String nextResourceName() {
    return getResourceName(nextResourceIndex);
  }

  void store(Writer output, String message) {
    PrintWriter writer = new PrintWriter(output);
    writer.printf("# %s\n", new Date());
    writer.printf("# %s\n ", message);

    writer.println();
    for (ArgInfo info : positionalInfos) {
      writer.printf("positional %s %s\n", info.className, info.fieldName);
    }

    writer.println();
    for (ArgInfo info : cmdLineInfos) {
      writer.printf("field %s %s\n", info.className, info.fieldName);
    }

    writer.println();
    for (ParserInfo info : parserInfos) {
      writer.printf("parser %s %s\n", info.parsedType, info.parserClass);
    }

    writer.println();
    for (VerifierInfo info : verifierInfos) {
      writer.printf("verifier %s %s %s\n",
          info.verifiedType, info.verifyingAnnotation, info.verifierClass);
    }
  }
}
