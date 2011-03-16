// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.args.parsers;

import java.lang.reflect.Type;
import java.util.List;

import com.twitter.common.args.TypeUtil;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Parser that makes implementation of parsers for parameterized types simpler.
 *
 * This only works for top-level parameters.  Behavior is undefined for nested parameterized types.
 * For example, this will handle {@code Map<String, Integer>} fine but will not handle
 * {@code Map<String, List<String>}.
 *
 * @author William Farner
 */
public abstract class TypeParameterizedParser<T> extends BaseParser<T> {

  private final int typeParamCount;

  /**
   * Creates a new type parameterized parser.
   *
   * @param parsedClass The parsed class.
   * @param typeParamCount Strict number of type parameters to allow on the assigned type.
   */
  TypeParameterizedParser(Class<T> parsedClass, int typeParamCount) {
    super(parsedClass);
    this.typeParamCount = typeParamCount;
  }

  /**
   * Performs the actual parsing.
   *
   * @param raw The raw value to parse.
   * @param paramParsers Parser classes for the parameter types, in the same order as the type
   *    parameters.
   * @return The parsed value.
   * @throws IllegalArgumentException If the value could not be parsed.
   */
  abstract T doParse(String raw, List<Class<?>> paramParsers) throws IllegalArgumentException;

  @Override public T parse(Type type, String raw) {
    List<Class<?>> typeParamClasses = TypeUtil.getTypeParamClasses(type);
    checkArgument(typeParamClasses.size() == typeParamCount, String.format(
        "Expected %d type parameters for %s but got %d",
        typeParamCount, type, typeParamClasses.size()));

    return doParse(raw, typeParamClasses);
  }
}
