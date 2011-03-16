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

import com.twitter.common.base.MorePreconditions;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks event statistics over a sliding window of time. An event is something that has a
 * frequency and associated total.
 *
 * @author William Farner
 */
public class SlidingStats {

  private final AtomicLong total;
  private final AtomicLong events;

  /**
   * Creates a new sliding statistic with the given name
   *
   * @param name Name for this stat collection.
   * @param totalUnitDisplay String to display for the total counter unit.
   */
  public SlidingStats(String name, String totalUnitDisplay) {
    MorePreconditions.checkNotBlank(name);

    String totalDisplay = name + "_" + totalUnitDisplay + "_total";
    String eventDisplay = name + "_events";
    total = Stats.exportLong(totalDisplay);
    events = Stats.exportLong(eventDisplay);
    Stats.export(Ratio.of(name + "_" + totalUnitDisplay + "_per_event",
        Rate.of(totalDisplay + "_per_sec", total).build(),
        Rate.of(eventDisplay + "_per_sec", events).build()));
  }

  public AtomicLong getTotalCounter() {
    return total;
  }

  public AtomicLong getEventCounter() {
    return events;
  }

  /**
   * Accumulates counter by an offset.  This is is useful for tracking things like
   * latency of operations.
   *
   * TODO(William Farner): Implement a wrapper to SlidingStats that expects to accumulate time, and can
   *    convert between time units.
   *
   * @param value The value to accumulate.
   */
  public synchronized void accumulate(long value) {
    total.addAndGet(value);
    events.incrementAndGet();
  }

  @Override
  public String toString() {
    return total + " " + events;
  }
}
