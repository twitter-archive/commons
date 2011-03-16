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

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class StatsTest {

  @After
  public void tearDown() {
    Stats.flush();
  }

  @Test
  public void testSimpleExport() {
    AtomicLong var = Stats.exportLong("test_long");
    assertCounter("test_long", 0);
    var.incrementAndGet();
    assertCounter("test_long", 1);
    var.addAndGet(100);
    assertCounter("test_long", 101);
  }

  @Test
  public void testNormalizesSpace() {
    AtomicLong leading = Stats.exportLong("  leading space");
    AtomicLong trailing = Stats.exportLong("trailing space   ");
    AtomicLong surround = Stats.exportLong("   surround space   ");

    leading.incrementAndGet();
    trailing.incrementAndGet();
    surround.incrementAndGet();
    assertCounter("__leading_space", 1);
    assertCounter("trailing_space___", 1);
    assertCounter("___surround_space___", 1);
  }

  @Test
  public void testNormalizesIllegalChars() {
    AtomicLong colon = Stats.exportLong("a:b");
    AtomicLong plus = Stats.exportLong("b+c");
    AtomicLong hyphen = Stats.exportLong("c-d");
    AtomicLong slash = Stats.exportLong("d/f");

    colon.incrementAndGet();
    plus.incrementAndGet();
    hyphen.incrementAndGet();
    slash.incrementAndGet();
    assertCounter("a_b", 1);
    assertCounter("b_c", 1);
    assertCounter("c_d", 1);
    assertCounter("d_f", 1);
  }

  private void assertCounter(String name, long value) {
    assertThat(Stats.<Long>getVariable(name).read(), is(value));
  }
}

