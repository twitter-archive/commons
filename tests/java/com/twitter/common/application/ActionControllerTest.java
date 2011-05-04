// =================================================================================================
// Copyright 2011 Twitter, Inc.
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

package com.twitter.common.application;

import com.twitter.common.base.Command;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class ActionControllerTest {
  private IMocksControl control;
  private ActionController actionController;

  @Before
  public void setUp() {
    control = createControl();
    actionController = new ActionController();
  }

  @Test
  public void testState() {
    Command action = control.createMock(Command.class);
    action.execute(); // should only execute once

    control.replay();

    actionController.addAction(action);
    actionController.execute();
    actionController.execute();

    try {
      actionController.addAction(action);
      fail("Should not be ablke to register shutdown actions after shutdown");
    } catch (IllegalStateException e) {
      // expected
    }

    control.verify();
  }

  @Test
  public void testShutdownOrdering() {
    control.checkOrder(true);

    Command registeredSecond = control.createMock(Command.class);
    registeredSecond.execute();

    Command registeredFirst = control.createMock(Command.class);
    registeredFirst.execute();

    control.replay();

    actionController.addAction(registeredFirst);
    actionController.addAction(registeredSecond);
    actionController.execute();

    control.verify();
  }

  @Test
  public void testAllActionsExecute() {
    Command action1 = control.createMock(Command.class);
    action1.execute();
    expectLastCall().andThrow(new RuntimeException());

    Command action2 = control.createMock(Command.class);
    action2.execute();
    expectLastCall().andThrow(new RuntimeException());

    control.replay();

    actionController.addAction(action1);
    actionController.addAction(action2);
    actionController.execute();

    control.verify();
  }
}
