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

package com.twitter.common.objectsize;

import java.util.LinkedList;

import junit.framework.TestCase;

public class ObjectSizeCalculatorTest extends TestCase {

  public void testObjectSize() {
    assertSizeIs(16, new Object());
  }

  public void testStringSize() {
    assertSizeIs(64, new String());
  }

  public static class Class1 {
    private boolean b1;
  }

  public void testOneBooleanSize() {
    assertSizeIs(24, new Class1());
  }

  public static class Class2 extends Class1 {
    private int i1;
  }

  public void testSimpleSubclassSize() {
    assertSizeIs(32, new Class2());
  }

  public void testZeroLengthArray() {
    assertSizeIs(24, new byte[0]);
    assertSizeIs(24, new int[0]);
    assertSizeIs(24, new long[0]);
    assertSizeIs(24, new Object[0]);
  }

  public void testByteArrays() {
    assertSizeIs(32, new byte[1]);
    assertSizeIs(32, new byte[8]);
    assertSizeIs(40, new byte[9]);
  }

  public void testCharArrays() {
    assertSizeIs(32, new char[1]);
    assertSizeIs(32, new char[4]);
    assertSizeIs(40, new char[5]);
  }

  public void testIntArrays() {
    assertSizeIs(32, new int[1]);
    assertSizeIs(32, new int[2]);
    assertSizeIs(40, new int[3]);
  }

  public void testLongArrays() {
    assertSizeIs(32, new long[1]);
    assertSizeIs(40, new long[2]);
    assertSizeIs(48, new long[3]);
  }

  public void testObjectArrays() {
    assertSizeIs(32, new Object[1]);
    assertSizeIs(40, new Object[2]);
    assertSizeIs(48, new Object[3]);
    assertSizeIs(32, new String[1]);
    assertSizeIs(40, new String[2]);
    assertSizeIs(48, new String[3]);
  }

  public static class Circular {
    Circular c;
  }

  public void testCircular() {
    Circular c1 = new Circular();
    c1.c = c1;
    assertSizeIs(24, c1);
  }

  public void testLongList() {
    LinkedList<Object> l = new LinkedList<Object>();
    for(int i = 0; i < 100000; ++i) {
      l.addLast(new Object());
    }
    assertSizeIs(5600080, l);
  }

  private static void assertSizeIs(long size, Object o) {
    assertEquals(size, ObjectSizeCalculator.getObjectSize(o));
  }
}
