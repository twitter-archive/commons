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

package com.twitter.common.application;

import com.twitter.common.base.ExceptionalCommand;

/**
 * Tracks actions to be run at a specific stage in an application lifecycle.
 *
 * @author John Sirois
 */
public interface ActionRegistry {

  /**
   * Adds the {@code action} to the list of actions to perform.
   *
   * @param action The action to register.
   */
  <E extends Exception, T extends ExceptionalCommand<E>> void addAction(T action);
}
