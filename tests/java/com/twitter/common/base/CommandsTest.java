// =================================================================================================
// Copyright 2013 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.base;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertNull;

public class CommandsTest extends EasyMockTest {
  private Command c1;
  private Command c2;
  private Command c3;

  @Before
  public void mySetup() {
    c1 = createMock(new Clazz<Command>() { });
    c2 = createMock(new Clazz<Command>() { });
    c3 = createMock(new Clazz<Command>() { });
  }

  @Test
  public void testAsSupplier() {
    c1.execute();

    control.replay();

    assertNull(Commands.asSupplier(c1).get());
  }

  @Test(expected =  NullPointerException.class)
  public void testAsSupplierPreconditions() {
    control.replay();

    Commands.asSupplier(null);
  }

  @Test
  public void testCompoundCommand() {
    c1.execute();
    c2.execute();
    c3.execute();

    control.replay();

    Commands.compound(Arrays.asList(c1, c2, c3)).execute();
  }

  @Test(expected = NullPointerException.class)
  public void testCompoundCommandPreconditions() {
    control.replay();

    Commands.compound(Arrays.asList(c1, null, c2));
  }

  @Test(expected = RuntimeException.class)
  public void testRuntimeException() {
    Command badCommand = createMock(new Clazz<Command>() { });

    c1.execute();
    badCommand.execute();
    expectLastCall().andThrow(new RuntimeException("Cannot Run"));

    control.replay();

    Commands.compound(Arrays.asList(c1, badCommand, c2)).execute();
  }
}
