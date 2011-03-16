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

import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import com.twitter.common.args.Parsers;
import com.twitter.common.args.Parsers.Parser;

import static com.twitter.common.args.Parsers.checkedGet;

/**
 * Set parser.
 *
 * @author William Farner
 */
public class SetParser extends TypeParameterizedParser<Set> {

  public SetParser() {
    super(Set.class, 1);
  }

  @Override
  Set doParse(String raw, List<Class<?>> paramParsers) {
    final Parser parser = checkedGet(paramParsers.get(0));

    return ImmutableSet.copyOf(Iterables.transform(Parsers.MULTI_VALUE_SPLITTER.split(raw),
        new Function<String, Object>() {
          @Override public Object apply(String raw) {
            return parser.parse(null, raw);
          }
        }));
  }
}
