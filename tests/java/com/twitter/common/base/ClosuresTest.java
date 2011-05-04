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

package com.twitter.common.base;

import java.io.IOException;

import com.google.common.base.Function;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;

/**
 * @author John Sirois
 */
public class ClosuresTest {
  private IMocksControl control;

  @Before
  public void setUp() {
    control = EasyMock.createControl();
  }

  @Test(expected = NullPointerException.class)
  public void testPreconditions() {
    Closures.asFunction(null);
  }

  @Test
  public void testApply() throws IOException {
    ExceptionalClosure<Integer, IOException> work = createMock(ExceptionalClosure.class);
    work.execute(1);
    control.replay();

    Function<Integer, Void> workFunction = Closures.asFunction(work);
    workFunction.apply(1);

    control.verify();
  }

  @Test
  public void testApplyThrows() throws IOException {
    ExceptionalClosure<Integer, IOException> work = createMock(ExceptionalClosure.class);
    work.execute(1);
    IOException ioException = new IOException();
    EasyMock.expectLastCall().andThrow(ioException);
    control.replay();

    Function<Integer, Void> workFunction = Closures.asFunction(work);
    try {
      workFunction.apply(1);
    } catch (RuntimeException e) {
      assertSame(ioException, e.getCause());
    }

    control.verify();
  }

  @Test
  public void testApplyThrowsTransparent() throws IOException {
    Closure<Integer> work = createMock(Closure.class);
    work.execute(1);
    RuntimeException runtimeException = new IllegalArgumentException();
    EasyMock.expectLastCall().andThrow(runtimeException);
    control.replay();

    Function<Integer, Void> workFunction = Closures.asFunction(work);
    try {
      workFunction.apply(1);
    } catch (RuntimeException e) {
      assertSame(runtimeException, e);
    }

    control.verify();
  }

  @SuppressWarnings("unchecked")
  private <T> T createMock(Class<? super T> type) {
    return (T) control.createMock(type);
  }
}
