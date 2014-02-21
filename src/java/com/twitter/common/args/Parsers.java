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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

import com.twitter.common.args.apt.Configuration;
import com.twitter.common.args.apt.Configuration.ParserInfo;

import static com.google.common.base.Preconditions.checkArgument;

import static com.twitter.common.args.apt.Configuration.ConfigurationException;

/**
 * A registry of Parsers for different supported argument types.
 *
 * @author William Farner
 */
public final class Parsers implements ParserOracle {

  public static final Splitter MULTI_VALUE_SPLITTER =
      Splitter.on(",").trimResults().omitEmptyStrings();

  private static final Function<ParserInfo, Class<?>> INFO_TO_PARSED_TYPE =
      new Function<ParserInfo, Class<?>>() {
        @Override public Class<?> apply(ParserInfo parserInfo) {
          try {
            return Class.forName(parserInfo.parsedType);
          } catch (ClassNotFoundException e) {
            throw new ConfigurationException(e);
          }
        }
      };

  @VisibleForTesting
  static final Function<ParserInfo, Parser<?>> INFO_TO_PARSER =
      new Function<ParserInfo, Parser<?>>() {
        @Override public Parser<?> apply(ParserInfo parserInfo) {
          try {
            Class<?> parserClass = Class.forName(parserInfo.parserClass);
            Constructor<?> constructor = parserClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Parser<?>) constructor.newInstance();
          } catch (ClassNotFoundException e) {
            throw new ConfigurationException(e);
          } catch (InstantiationException e) {
            throw new ConfigurationException(e);
          } catch (IllegalAccessException e) {
            throw new ConfigurationException(e);
          } catch (NoSuchMethodException e) {
            throw new ConfigurationException(e);
          } catch (InvocationTargetException e) {
            throw new ConfigurationException(e);
          }
        }
      };

  private final ImmutableMap<Class<?>, Parser<?>> registry;

  /**
   * Creates a new parser registry over the specified {@code parsers}.
   *
   * @param parsers The parsers to register.
   */
  public Parsers(Map<Class<?>, Parser<?>> parsers) {
    Preconditions.checkNotNull(parsers);
    registry = ImmutableMap.copyOf(parsers);
  }

  @Override
  public <T> Parser<T> get(TypeToken<T> type) throws IllegalArgumentException {
    Parser<?> parser;
    Class<?> explicitClass = type.getRawType();
    while (((parser = registry.get(explicitClass)) == null) && (explicitClass != null)) {
      explicitClass = explicitClass.getSuperclass();
    }
    checkArgument(parser != null, "No parser found for " + type);

    // We control loading of the registry which ensures a proper mapping of class -> parser
    @SuppressWarnings("unchecked")
    Parser<T> parserT = (Parser<T>) parser;

    return parserT;
  }

  static Parsers fromConfiguration(Configuration configuration) {
    Map<Class<?>, Parser<?>> parsers =
        Maps.transformValues(
            Maps.uniqueIndex(configuration.parserInfo(), INFO_TO_PARSED_TYPE),
            INFO_TO_PARSER);
    return new Parsers(parsers);
  }
}
