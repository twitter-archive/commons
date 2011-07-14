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

import java.lang.reflect.Type;
import java.util.Set;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.twitter.common.args.parsers.AmountParser;
import com.twitter.common.args.parsers.BooleanParser;
import com.twitter.common.args.parsers.ByteParser;
import com.twitter.common.args.parsers.CharacterParser;
import com.twitter.common.args.parsers.ClassParser;
import com.twitter.common.args.parsers.DateParser;
import com.twitter.common.args.parsers.DoubleParser;
import com.twitter.common.args.parsers.EnumParser;
import com.twitter.common.args.parsers.FileParser;
import com.twitter.common.args.parsers.FloatParser;
import com.twitter.common.args.parsers.InetSocketAddressParser;
import com.twitter.common.args.parsers.IntegerParser;
import com.twitter.common.args.parsers.ListParser;
import com.twitter.common.args.parsers.LongParser;
import com.twitter.common.args.parsers.MapParser;
import com.twitter.common.args.parsers.PairParser;
import com.twitter.common.args.parsers.SetParser;
import com.twitter.common.args.parsers.ShortParser;
import com.twitter.common.args.parsers.StringParser;
import com.twitter.common.args.parsers.URIParser;
import com.twitter.common.args.parsers.URLParser;
import com.twitter.common.args.parsers.UnitParser;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Parsers for different supported argument types.
 *
 * TODO(William Farner): Add a mechanism for global registration of custom parsers.
 *
 * @author William Farner
 */
public final class Parsers {

  private Parsers() {
    // Utility
  }

  /**
   * Gets the parser associated with a class.
   *
   * @param cls Class to get the parser for.
   * @return The parser for {@code cls} or {@code null} if no parser was found for that type or any
   *     of its supertypes.
   */
  static Parser get(Class<?> cls) {
    Parser parser;
    while (((parser = REGISTRY.get(cls)) == null) && (cls != null)) {
      cls = cls.getSuperclass();
    }
    return parser;
  }

  /**
   * Gets the parser associatd with a class.
   *
   * @param cls Class to get the parser for.
   * @return The parser for {@code cls} or {@code null} if no parser was found for that type.
   * @throws IllegalArgumentException If no parser was found for {@code cls}.
   */
  public static Parser checkedGet(Class<?> cls) throws IllegalArgumentException {
    Parser parser = get(cls);
    checkArgument(parser != null, "No parser found for class " + cls);
    return parser;
  }

  /**
   * An interface to a command line argument parser.
   *
   * @param <T> The base class this parser can parse all subtypes of.
   */
  public interface Parser<T> {

    /**
     * Parses strings as arguments of a given subtype of {@code T}.
     *
     * @param type The target type of the parsed argument.
     * @param raw The raw value of the argument.
     * @return A value of the given type parsed from the raw value.
     * @throws IllegalArgumentException if the raw value could not be parsed into a value of the
     *     given type.
     */
    T parse(Type type, String raw) throws IllegalArgumentException;

    /**
     * Returns the root of the class hierarchy this parser handles.
     *
     * <p>Note that implementations that return a proper supertype class instead of T.class must
     * be able to handle {@link #parse(java.lang.reflect.Type, String) parsing} raw values into
     * all possible subtypes in use as command line argument types.
     *
     * @return The base class this parser can parse all subtypes of.
     */
    Class<? super T> getParsedClass();
  }

  private static final Set<Class<? extends Parser>> PARSER_CLASSES =
      ImmutableSet.<Class<? extends Parser>>builder()
      .add(AmountParser.class)
      .add(BooleanParser.class)
      .add(ByteParser.class)
      .add(CharacterParser.class)
      .add(ClassParser.class)
      .add(DateParser.class)
      .add(DoubleParser.class)
      .add(EnumParser.class)
      .add(FileParser.class)
      .add(FloatParser.class)
      .add(InetSocketAddressParser.class)
      .add(IntegerParser.class)
      .add(ListParser.class)
      .add(LongParser.class)
      .add(MapParser.class)
      .add(PairParser.class)
      .add(SetParser.class)
      .add(ShortParser.class)
      .add(StringParser.class)
      .add(UnitParser.class)
      .add(URIParser.class)
      .add(URLParser.class)
      .build();
  private static final ImmutableMap<Class<?>, Parser> REGISTRY;
  static {
    ImmutableMap.Builder<Class<?>, Parser> registryBuilder =
      ImmutableMap.builder();
    for (Class<? extends Parser> parserClass : PARSER_CLASSES) {
      try {
        Parser parser = parserClass.newInstance();
        registryBuilder.put(parser.getParsedClass(), parser);
      } catch (InstantiationException e) {
        throw new RuntimeException("Failed to instantiate parser class " + parserClass, e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException("No access to constructor on parser " + parserClass, e);
      }
    }
    REGISTRY = registryBuilder.build();
  }

  public static final Splitter MULTI_VALUE_SPLITTER = Splitter.on(",")
      .trimResults().omitEmptyStrings();
}
