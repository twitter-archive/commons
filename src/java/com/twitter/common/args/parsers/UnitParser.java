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

import java.util.EnumSet;

import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;
import com.twitter.common.quantity.Unit;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Unit parser.
 *
 * @author William Farner
 */
public class UnitParser extends NonParameterizedTypeParser<Unit> {

  public UnitParser() {
    super(Unit.class);
  }

  @Override
  public Unit doParse(String raw) {
    Unit unit = null;
    try {
      unit = Time.valueOf(raw);
    } catch (IllegalArgumentException e) {
      try {
        unit = Data.valueOf(raw);
      } catch (IllegalArgumentException x) {
        // No-op.
      }
    }

    checkArgument(unit != null, String.format(
        "No Units found matching %s, options: (Time): %s, (Data): %s",
        raw, EnumSet.allOf(Time.class), EnumSet.allOf(Data.class)));
    return unit;
  }
}
