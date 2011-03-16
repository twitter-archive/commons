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

import com.google.common.collect.ImmutableList;

import com.twitter.common.args.Parsers;
import com.twitter.common.args.Parsers.Parser;
import com.twitter.common.collections.Pair;

import static com.google.common.base.Preconditions.checkArgument;
import static com.twitter.common.args.Parsers.checkedGet;

/**
 * Pair parser.
 *
 * @author William Farner
 */
public class PairParser extends TypeParameterizedParser<Pair> {

  public PairParser() {
    super(Pair.class, 2);
  }

  @Override
  Pair doParse(String raw, List<Class<?>> paramParsers) {
    final Parser leftParser = checkedGet(paramParsers.get(0));
    final Parser rightParser = checkedGet(paramParsers.get(1));

    List<String> parts = ImmutableList.copyOf(Parsers.MULTI_VALUE_SPLITTER.split(raw));
    checkArgument(parts.size() == 2,
        "Only two values may be specified for a pair, you gave " + parts.size());

    return Pair.of(leftParser.parse(null, parts.get(0)),
        rightParser.parse(null, parts.get(1)));
  }
}
