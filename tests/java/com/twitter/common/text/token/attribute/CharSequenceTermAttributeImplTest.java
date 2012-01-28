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

package com.twitter.common.text.token.attribute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;

import org.junit.Test;

public class CharSequenceTermAttributeImplTest {
  private CharSequenceTermAttributeImpl att = new CharSequenceTermAttributeImpl();

  @Test
  public void testSetTermBuffer() {
    String test = "this is a test";
    CharSequence testSeq = (CharSequence) test;

    att.setTermBuffer(testSeq);
    assertCharSequence(test);
    assertTerm(test, 0, test.length());

    att.setTermBuffer(testSeq, 1, 3);
    assertCharSequence(test);
    assertTerm("his", 1, 3);
  }

  @Test
  public void testOffsetAndLength() {
    att.setTermBuffer("test");
    assertTerm("test", 0, 4);

    att.setLength(3);
    assertTerm("tes", 0, 3);

    att.setOffset(1);
    assertTerm("est", 1, 3);

    att.clear();
    assertTerm("", 0, 0);

    try {
      att.setOffset(-1);
      fail("Expected an IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }

    try {
      att.setOffset(5);
      fail("Expected an IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }

    try {
      att.setLength(-1);
      fail("Expected an IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }

    try {
      att.setLength(5);
      fail("Expected an IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }

    att.setOffset(1);
    att.setLength(4);
    assertEquals(4, att.getLength());
    try {
      att.getTermCharSequence();
      fail("Expected a StringIndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      // expected because, even though the offset and length are both individually valid, the span
      // defined by them is not completely contained within the underlying CharSequence.
    }
  }

  @Test
  public void testCopyTo() {
    CharSequenceTermAttributeImpl otherAtt = new CharSequenceTermAttributeImpl();
    otherAtt.setTermBuffer("test");

    otherAtt.copyTo(att);

    assertCharSequence("test");
    assertTerm("test", 0, 4);
  }

  @Test
  public void testEquality() {
    CharSequence test = "test";
    att.setTermBuffer(test);

    CharSequenceTermAttributeImpl otherAtt = new CharSequenceTermAttributeImpl();
    otherAtt.setTermBuffer(test);

    assertEquality(otherAtt); // CharSequences are the same object

    otherAtt.setTermBuffer("test");
    assertEquality(otherAtt); // CharSequences are char-wise equal

    otherAtt.setTermBuffer("test_", 0, 4);
    assertEquality(otherAtt); // Within the span, the CharSequences are char-wise equal

    otherAtt.setTermBuffer(test, 0, 3);
    assertInequality(otherAtt); // different offset

    otherAtt.setTermBuffer(test, 1, 3);
    assertInequality(otherAtt); // different offset

    assertFalse(att.equals(null));
    assertEquality(att);
  }

  @Test
  public void testHashCode() {
    CharSequence test = "This is for test.";
    att.setTermBuffer(test);
    int hashCode = att.hashCode();

    att.setOffset(2);
    att.setLength(3);
    assertTrue(hashCode != att.hashCode());

    att.setOffset(0);
    att.setLength(test.length());
    assertTrue(hashCode == att.hashCode());

    att.setTermBuffer(test, 2, 3);
    assertTrue(hashCode != att.hashCode());

    att.setTermBuffer(test, 0, test.length());
    assertTrue(hashCode == att.hashCode());
  }

  private void assertTerm(String term, int offset, int length) {
    assertEquals(term, att.getTermString());
    assertEquals(offset, att.getOffset());
    assertEquals(length, att.getLength());
  }

  private void assertCharSequence(CharSequence sequence) {
    CharSequence attrSequence = att.getCharSequence();
    assertEquals(attrSequence.length(), sequence.length());
    for (int i = 0; i < sequence.length(); i++) {
      assertEquals(attrSequence.charAt(i), sequence.charAt(i));
    }
  }

  private void assertEquality(Object otherAtt) {
    assertTrue(att.equals(otherAtt));
    assertTrue(att.hashCode() == otherAtt.hashCode());
  }

  private void assertInequality(Object otherAtt) {
    assertFalse(att.equals(otherAtt));
    assertFalse(att.hashCode() == otherAtt.hashCode());
  }

  @Test(expected=NotSerializableException.class)
  public void testCannotSerialize() throws IOException {
    // Make sure that serialization throws an exception.
    ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream());
    oos.writeObject(new CharSequenceTermAttributeImpl());
    oos.close();
  }
}

