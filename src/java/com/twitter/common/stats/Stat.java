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
 * A stat that may only be read, no method calls will cause any internal state changes.
 *
 * @author William Farner
 */
public interface Stat<T> {

  /**
   * Gets the name of this stat. For sake of convention, variable names should be alphanumeric, and
   * use underscores.
   *
   * @return The variable name.
   */
  public String getName();

  /**
   * Retrieves the most recent value of the stat.
   *
   * @return The most recent value.
   */
  public T read();
}
