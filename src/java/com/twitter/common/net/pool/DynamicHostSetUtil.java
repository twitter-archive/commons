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

package com.twitter.common.net.pool;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Util Class for dealing with dynamic sets of hosts.
 *
 * @author Florian Leibert
 * @author Jake Mannix
 */
public class DynamicHostSetUtil {

  /**
   * Gets a snapshot of a set of dynamic hosts (e.g. a ServerSet) and returns a readable copy of
   * the underlying actual endpoints.
   * @param hostSet
   * @param <T>
   * @return
   * @throws DynamicHostSet.MonitorException
   */
  public static <T> ImmutableSet<T> getSnapshot(DynamicHostSet<T> hostSet)
      throws DynamicHostSet.MonitorException {
    final Set<T> set = Sets.newHashSet();
    hostSet.monitor(new DynamicHostSet.HostChangeMonitor<T>() {
      @Override
      public void onChange(ImmutableSet<T> hostSet) {
        set.addAll(hostSet);
      }
    });
    return ImmutableSet.copyOf(set);
  }
}
