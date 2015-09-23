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

package com.twitter.common.thrift.callers;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.easymock.EasyMockTest;
import org.junit.Before;

import java.lang.reflect.Method;

import static org.easymock.EasyMock.expect;

/**
 * Test framework for testing callers.
 *
 * @author William Farner
 */
public abstract class AbstractCallerTest extends EasyMockTest {
  protected final Amount<Long, Time> CONNECT_TIMEOUT = Amount.of(1L, Time.HOURS);

  protected Caller caller;

  protected Method methodA;
  protected Object[] argsA;

  @Before
  public final void callerSetUp() throws Exception {
    caller = createMock(Caller.class);
    methodA = Object.class.getMethod("toString");
    argsA = new Object[] {};
  }

  protected String call(Caller caller) throws Throwable {
    return (String) caller.call(methodA, argsA, null, CONNECT_TIMEOUT);
  }

  protected void expectCall(String returnValue) throws Throwable {
    expect(caller.call(methodA, argsA, null, CONNECT_TIMEOUT)).andReturn(returnValue);
  }

  protected void expectCall(Throwable thrown) throws Throwable {
    expect(caller.call(methodA, argsA, null, CONNECT_TIMEOUT)).andThrow(thrown);
  }
}
