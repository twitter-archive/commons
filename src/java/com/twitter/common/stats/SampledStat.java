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

package com.twitter.common.stats;

/**
 * A convenience class to perform the basic tasks needed for a {@link RecordingStat} except the
 * actual value calculation.
 *
 * @author William Farner
 */
abstract class SampledStat<T extends Number> extends StatImpl<T> implements RecordingStat<T> {

  private T prevValue;

  public SampledStat(String name, T defaultValue) {
    super(name);
    this.prevValue = defaultValue; /* Don't forbid null. */
  }

  public abstract T doSample();

  @Override
  public final T sample() {
    prevValue = doSample();
    return prevValue;
  }

  @Override
  public T read() {
    return prevValue;
  }
}
