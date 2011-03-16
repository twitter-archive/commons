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

package com.twitter.common.testing;

import com.google.common.collect.ImmutableList;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author John Sirois
 */
public class EasyMockTestTest extends EasyMockTest {

  @Test
  public void testSimplyParametrizedMock() {
    final AtomicBoolean ran = new AtomicBoolean(false);

    Runnable runnable = createMock(new Clazz<Runnable>() {});
    runnable.run();
    expectLastCall().andAnswer(new IAnswer<Void>() {
      @Override public Void answer() throws Throwable {
        ran.set(true);
        return null;
      }
    });
    control.replay();

    runnable.run();
    assertTrue(ran.get());
  }

  @Test
  public void testNestedParametrizedMock() {
    List<List<String>> list = createMock(new Clazz<List<List<String>>>() {});
    EasyMock.expect(list.get(0)).andReturn(ImmutableList.of("jake"));
    control.replay();

    assertEquals(ImmutableList.of("jake"), list.get(0));
  }
}
