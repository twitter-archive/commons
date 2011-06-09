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

package com.twitter.common.objectsize;

import java.util.LinkedList;

import junit.framework.TestCase;

/**
 * @author Attila Szegedi
 *
 */
public class ObjectSizeCalculatorTest extends TestCase {
  private static int A = ObjectSizeCalculator.getArrayHeaderSize();
  private static int O = ObjectSizeCalculator.getObjectHeaderSize();
  private static int R = ObjectSizeCalculator.getReferenceSize();
  private static int S = ObjectSizeCalculator.getSuperclassFieldPadding();

  public void testRounding() {
    assertEquals(0, roundTo(0, 8));
    assertEquals(8, roundTo(1, 8));
    assertEquals(8, roundTo(7, 8));
    assertEquals(8, roundTo(8, 8));
    assertEquals(16, roundTo(9, 8));
    assertEquals(16, roundTo(15, 8));
    assertEquals(16, roundTo(16, 8));
    assertEquals(24, roundTo(17, 8));
  }

  public void testObjectSize() {
    assertSizeIs(O, new Object());
  }

  public void testStringSize() {
    // String has 3 int fields and one reference field
    assertSizeIs(O + 3 * 4 + R + A, new String());
  }

  public static class Class1 {
    private boolean b1;
  }

  public void testOneBooleanSize() {
    assertSizeIs(O + 1, new Class1());
  }

  public static class Class2 extends Class1 {
    private int i1;
  }

  public void testSimpleSubclassSize() {
    assertSizeIs(O + roundTo(1, S) + 4, new Class2());
  }

  public void testZeroLengthArray() {
    assertSizeIs(A, new byte[0]);
    assertSizeIs(A, new int[0]);
    assertSizeIs(A, new long[0]);
    assertSizeIs(A, new Object[0]);
  }

  public void testByteArrays() {
    assertSizeIs(A + 1, new byte[1]);
    assertSizeIs(A + 8, new byte[8]);
    assertSizeIs(A + 9, new byte[9]);
  }

  public void testCharArrays() {
    assertSizeIs(A + 2 * 1, new char[1]);
    assertSizeIs(A + 2 * 4, new char[4]);
    assertSizeIs(A + 2 * 5, new char[5]);
  }

  public void testIntArrays() {
    assertSizeIs(A + 4 * 1, new int[1]);
    assertSizeIs(A + 4 * 2, new int[2]);
    assertSizeIs(A + 4 * 3, new int[3]);
  }

  public void testLongArrays() {
    assertSizeIs(A + 8 * 1, new long[1]);
    assertSizeIs(A + 8 * 2, new long[2]);
    assertSizeIs(A + 8 * 3, new long[3]);
  }

  public void testObjectArrays() {
    assertSizeIs(A + R * 1, new Object[1]);
    assertSizeIs(A + R * 2, new Object[2]);
    assertSizeIs(A + R * 3, new Object[3]);
    assertSizeIs(A + R * 1, new String[1]);
    assertSizeIs(A + R * 2, new String[2]);
    assertSizeIs(A + R * 3, new String[3]);
  }

  public static class Circular {
    Circular c;
  }

  public void testCircular() {
    Circular c1 = new Circular();
    long size = ObjectSizeCalculator.getObjectSize(c1);
    c1.c = c1;
    assertEquals(size, ObjectSizeCalculator.getObjectSize(c1));
  }

  public void testLongList() {
    LinkedList<Object> l = new LinkedList<Object>();
    for(int i = 0; i < 100000; ++i) {
      l.addLast(new Object());
    }
    assertSizeIs(roundTo(O + 4 + R, 8) + roundTo(O + 3 * R, 8) * 100001 +
        roundTo(O, 8) * 100000, l);
  }

  private static void assertSizeIs(long size, Object o) {
    assertEquals(roundTo(size, 8), ObjectSizeCalculator.getObjectSize(o));
  }

  private static long roundTo(long x, int multiple) {
    return ObjectSizeCalculator.roundTo(x, multiple);
  }
}
