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

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author John Sirois
 */
public class ExceptionalFunctionsTest {

  private static final String STRING = "jake";
  private ExceptionalFunction<String, Integer, NumberFormatException> stringToInt =
      new ExceptionalFunction<String, Integer, NumberFormatException>() {
        @Override
        public Integer apply(String item) throws NumberFormatException {
          return Integer.parseInt(item);
        }
      };
  private ExceptionalFunction<Integer, Double, Exception> intToDouble =
      new ExceptionalFunction<Integer, Double, Exception>() {
        @Override
        public Double apply(Integer item) throws NumberFormatException {
          return item.doubleValue();
        }
      };
  private IMocksControl control;
  private ExceptionalFunction<String, Integer, IOException> function;

  @Before
  public void setUp() throws Exception {
    control = EasyMock.createControl();

    @SuppressWarnings("unchecked")
    ExceptionalFunction<String, Integer, IOException> f =
        control.createMock(ExceptionalFunction.class);
    this.function = f;
  }

  @Test
  public void testCurryLazy() throws IOException {
    control.replay();

    ExceptionalFunctions.curry(function, STRING);

    control.verify();
  }

  @Test
  public void testCurryExecution() throws IOException {
    expect(function.apply(STRING)).andReturn(1);
    expect(function.apply(STRING)).andReturn(2);

    control.replay();

    CallableExceptionalSupplier<Integer, IOException> supplier =
        ExceptionalFunctions.curry(function, STRING);

    assertEquals("curried function should be called", Integer.valueOf(1), supplier.get());
    assertEquals("curried function should not be memoized", Integer.valueOf(2), supplier.get());

    control.verify();
  }

  @Test
  public void testCurryException() throws IOException {
    IOException ioException = new IOException();
    expect(function.apply(STRING)).andThrow(ioException);

    RuntimeException runtimeException = new IllegalStateException();
    expect(function.apply(STRING)).andThrow(runtimeException);

    control.replay();

    CallableExceptionalSupplier<Integer, IOException> supplier =
        ExceptionalFunctions.curry(function, STRING);

    try {
      supplier.get();
    } catch (IOException e) {
      assertSame("Expected exception propagation to be transparent", ioException, e);
    }

    try {
      supplier.get();
    } catch (IllegalStateException e) {
      assertSame("Expected exception propagation to be transparent", runtimeException, e);
    }

    control.verify();
  }

  @Test
  public void testCompose() throws Exception {
    ExceptionalFunction<String, Double, Exception> stringToDouble =
        ExceptionalFunctions.compose(intToDouble, stringToInt);
    assertEquals(new Double(2d), stringToDouble.apply("2"));
    assertEquals(new Double(-1d), stringToDouble.apply("-1"));
  }

  @Test(expected = NumberFormatException.class)
  public void testComposeWithException() throws Exception {
    ExceptionalFunction<String, Double, Exception> stringToDouble =
        ExceptionalFunctions.compose(intToDouble, stringToInt);
    stringToDouble.apply("1.1");
  }

  @Test
  public void testForFunction() {
    com.google.common.base.Function<String, Integer> stringToIntegerGuavaFunction =
        new com.google.common.base.Function<String, Integer>() {
          @Override
          public Integer apply(String item) {
            return Integer.parseInt(item);
          }
        };
    ExceptionalFunction<String, Integer, NumberFormatException> stringToIntegerExceptionalFunction =
        ExceptionalFunctions.forFunction(stringToIntegerGuavaFunction);
    assertEquals(
        stringToIntegerGuavaFunction.apply("1234"),
        stringToIntegerExceptionalFunction.apply("1234"));
  }

  @Test
  public void testConstantReturn() {
    final String value = "taxes?";
    ExceptionalFunction<String, String, IllegalArgumentException> constant =
        ExceptionalFunctions.constant(value);
    assertEquals(value, constant.apply("yes"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstantException() {
    ExceptionalFunction<String, String, IllegalArgumentException> badness =
        ExceptionalFunctions.forException(new IllegalArgumentException());
    badness.apply("pleaseno");
  }

  @Test
  public void testConstantExceptionReturn() {
    final Exception value = new Exception("corner case?");
    ExceptionalFunction<String, Exception, IllegalArgumentException> constant =
        ExceptionalFunctions.constant(value);
    assertEquals(value, constant.apply("yes"));
  }
}
