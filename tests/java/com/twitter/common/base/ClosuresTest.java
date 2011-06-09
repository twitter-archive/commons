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
import com.google.common.base.Predicate;

import org.easymock.EasyMock;
import org.junit.Test;

import com.twitter.common.testing.EasyMockTest;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class ClosuresTest extends EasyMockTest {

  private static final Clazz<Closure<Integer>> INT_CLOSURE_CLZ = new Clazz<Closure<Integer>>() { };
  private static final Clazz<ExceptionalClosure<Integer, IOException>> EXC_INT_CLOSURE_CLZ =
      new Clazz<ExceptionalClosure<Integer, IOException>>() { };

  @Test(expected = NullPointerException.class)
  public void testPreconditions() {
    control.replay();

    Closures.asFunction(null);
  }

  @Test
  public void testApply() throws IOException {
    ExceptionalClosure<Integer, IOException> work = createMock(EXC_INT_CLOSURE_CLZ);
    work.execute(1);
    control.replay();

    Function<Integer, Void> workFunction = Closures.asFunction(work);
    workFunction.apply(1);
  }

  @Test
  public void testApplyThrows() throws IOException {
    ExceptionalClosure<Integer, IOException> work = createMock(EXC_INT_CLOSURE_CLZ);
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
  }

  @Test
  public void testApplyThrowsTransparent() throws IOException {
    Closure<Integer> work = createMock(INT_CLOSURE_CLZ);
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
  }

  @Test
  public void testCombine() {
    Closure<Integer> work1 = createMock(INT_CLOSURE_CLZ);
    Closure<Integer> work2 = createMock(INT_CLOSURE_CLZ);

    Closure<Integer> wrapper = Closures.combine(work1, work2);

    work1.execute(1);
    work2.execute(1);

    work1.execute(2);
    work2.execute(2);

    control.replay();

    wrapper.execute(1);
    wrapper.execute(2);
  }

  @Test
  public void testCombineOneThrows() {
    Closure<Integer> work1 = createMock(INT_CLOSURE_CLZ);
    Closure<Integer> work2 = createMock(INT_CLOSURE_CLZ);
    Closure<Integer> work3 = createMock(INT_CLOSURE_CLZ);

    Closure<Integer> wrapper = Closures.combine(work1, work2, work3);

    work1.execute(1);
    expectLastCall().andThrow(new RuntimeException());

    work1.execute(2);
    work2.execute(2);
    expectLastCall().andThrow(new RuntimeException());

    work1.execute(3);
    work2.execute(3);
    work3.execute(3);
    expectLastCall().andThrow(new RuntimeException());

    control.replay();

    try {
      wrapper.execute(1);
      fail("Should have thrown.");
    } catch (RuntimeException e) {
      // Expected.
    }

    try {
      wrapper.execute(2);
      fail("Should have thrown.");
    } catch (RuntimeException e) {
      // Expected.
    }

    try {
      wrapper.execute(3);
      fail("Should have thrown.");
    } catch (RuntimeException e) {
      // Expected.
    }
  }

  @Test
  public void testFilter() {
    Predicate<Integer> filter = createMock(new Clazz<Predicate<Integer>>() { });
    Closure<Integer> work = createMock(INT_CLOSURE_CLZ);

    expect(filter.apply(1)).andReturn(true);
    work.execute(1);

    expect(filter.apply(2)).andReturn(false);

    Closure<Integer> filtered = Closures.filter(filter, work);

    control.replay();

    filtered.execute(1);
    filtered.execute(2);
  }
}
