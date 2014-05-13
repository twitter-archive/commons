// =================================================================================================
// Copyright 2014 Twitter, Inc.
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

package com.twitter.common.net.pool;

import com.google.common.collect.ImmutableSet;

import org.easymock.Capture;
import org.easymock.IAnswer;
import org.junit.Test;

import com.twitter.common.base.Command;
import com.twitter.common.net.pool.DynamicHostSet.HostChangeMonitor;
import com.twitter.common.net.pool.DynamicHostSet.MonitorException;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;

public class DynamicHostSetUtilTest extends EasyMockTest {

  @Test
  public void testSnapshot() throws MonitorException {
    DynamicHostSet<String> hostSet = createMock(new Clazz<DynamicHostSet<String>>() { });
    final Capture<HostChangeMonitor<String>> monitorCapture = createCapture();
    final Command unwatchCommand = createMock(Command.class);

    expect(hostSet.watch(capture(monitorCapture))).andAnswer(new IAnswer<Command>() {
      @Override public Command answer() throws Throwable {
        // Simulate the 1st blocking onChange callback.
        HostChangeMonitor<String> monitor = monitorCapture.getValue();
        monitor.onChange(ImmutableSet.of("jack", "jill"));
        return unwatchCommand;
      }
    });

    // Confirm we clean up our watch.
    unwatchCommand.execute();
    expectLastCall();

    control.replay();

    ImmutableSet<String> snapshot = DynamicHostSetUtil.getSnapshot(hostSet);
    assertEquals(ImmutableSet.of("jack", "jill"), snapshot);
  }
}
