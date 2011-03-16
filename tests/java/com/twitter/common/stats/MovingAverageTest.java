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

import com.google.common.collect.Lists;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.util.List;

/**
 * Test for MovingAverage.
 *
 * @author William Farner
 */
public class MovingAverageTest {

  private IMocksControl control;

  private Stat<Integer> input;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    control = createControl();
    input = control.createMock(Stat.class);
  }

  @After
  public void verify() {
    control.verify();
  }

  @Test
  public void testEmptySeries() {
    runTest(Lists.<Integer>newArrayList(), Lists.<Double>newArrayList());
  }

  @Test
  public void testConstantValues() {
    runTest(
        Lists.newArrayList( 5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5),
        Lists.newArrayList(5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d));
  }

  @Test
  public void testLinear() {
    runTest(
      Lists.newArrayList( 1,    2,  3,    4,  5,    6,  7,    8,  9,   10,   11,   12,   13,   14),
      Lists.newArrayList(1d, 1.5d, 2d, 2.5d, 3d, 3.5d, 4d, 4.5d, 5d, 5.5d, 6.5d, 7.5d, 8.5d, 9.5d));
  }

  @Test
  public void testStep() {
    runTest(
        Lists.newArrayList( 0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, 10, 10, 10, 10, 10),
        Lists.newArrayList(0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 1d, 2d, 3d, 4d, 5d));
  }

  private void runTest(final List<Integer> inputs, List<Double> expectedOutputs) {
    expect(input.getName()).andReturn("test");
    expectLastCall().atLeastOnce();
    for (int value : inputs) {
      expect(input.read()).andReturn(value);
    }

    control.replay();

    MovingAverage<Integer> movingAvg = MovingAverage.of(input, 10 /* window size */);

    for (double output : expectedOutputs) {
      assertThat(movingAvg.sample(), is(output));
    }
  }
}
