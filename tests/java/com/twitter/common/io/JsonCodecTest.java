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

package com.twitter.common.io;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.Test;

import static com.twitter.common.io.CodecTestUtilities.serialize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JsonCodecTest {
  @Test
  public void testRoundTrip() throws IOException {
    TestClass testOut = createTestClassInstance();
    Codec<TestClass> codec = JsonCodec.create(TestClass.class);
    TestClass testIn = CodecTestUtilities.roundTrip(codec, testOut);
    assertEquals(testOut.data1, testIn.data1);
    assertEquals(testOut.data2, testIn.data2);
    assertEquals(testOut.data3, testIn.data3);
    assertTrue(Arrays.equals(testOut.data4, testIn.data4));
  }

  @Test
  public void testExpectedFormat() throws IOException {
    Codec<TestClass> codec = JsonCodec.create(TestClass.class);
    TestClass item = createTestClassInstance();
    JsonElement expectedElement = new JsonParser()
        .parse("{\"data1\":\"foo\",\"data2\":\"bar\",\"data3\":42,\"data4\":[\"abc\",\"def\"]}");
    JsonElement actualElement = new JsonParser().parse(new String(serialize(codec, item), "utf-8"));
    assertEquals(expectedElement.toString(), actualElement.toString());
  }

  private TestClass createTestClassInstance() {
    TestClass testOut = new TestClass();
    testOut.data1 = "foo";
    testOut.data2 = "bar";
    testOut.data3 = 42;
    testOut.data4 = new String[] { "abc", "def" };
    return testOut;
  }

  @Test
  public void testThriftExclusionWrongFieldClass() throws IOException {
    ThriftTestClass1 test1 = new ThriftTestClass1();
    test1.data1 = "foo";
    test1.__isset_bit_vector = "bar";
    assertEquals("foo", roundTrip(test1).data1);
    assertEquals("bar", roundTrip(test1).__isset_bit_vector);
  }

  @Test
  public void testThriftExclusionRightFieldClass() throws IOException {
    ThriftTestClass2 test2 = new ThriftTestClass2();
    test2.data1 = "foo";
    test2.__isset_bit_vector = new BitSet(1);
    assertEquals("foo", roundTrip(test2).data1);
    assertNull(roundTrip(test2).__isset_bit_vector);
  }

  private static <T> T roundTrip(T item) throws IOException {
    @SuppressWarnings("unchecked")
    Class<T> itemType = (Class<T>) item.getClass();
    return CodecTestUtilities.roundTrip(JsonCodec.create(itemType,
        new GsonBuilder()
            .setExclusionStrategies(JsonCodec.getThriftExclusionStrategy())
            .create()), item);
  }

  public static class TestClass {
    private String data1;
    private String data2;
    private int data3;
    private String[] data4;
  }

  public static class ThriftTestClass1 {
    private String data1;
    private String __isset_bit_vector;
  }

  public static class ThriftTestClass2 {
    private String data1;
    private BitSet __isset_bit_vector;
  }
}
