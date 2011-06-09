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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

import com.google.common.base.Predicate;

import org.junit.Test;

import static com.twitter.common.io.CodecTestUtilities.deserialize;
import static com.twitter.common.io.CodecTestUtilities.serialize;
import static org.junit.Assert.assertEquals;

public class CompatibilityCodecTest {
  private final Codec<TestClass> primaryCodec = new SimpleCodec('x');
  private final Codec<TestClass> secondaryCodec = new SimpleCodec('y');
  private final Codec<TestClass> compatibilityCodec = CompatibilityCodec.create(primaryCodec,
      secondaryCodec, 1, new Predicate<byte[]>() {
        @Override
        public boolean apply(byte[] input) {
          return input.length > 0 && input[0] == 'x';
        }
      });
  private final TestClass t = new TestClass();
  {
    t.data = "foo";
  }

  @Test
  public void testCompatibilityDeserializesSecondary() throws IOException {
    assertCanDeserialize(compatibilityCodec, secondaryCodec);
  }

  @Test
  public void testCompatibilityDeserializesPrimary() throws IOException {
    assertCanDeserialize(compatibilityCodec, primaryCodec);
  }

  @Test
  public void testCompatibilitySerializesPrimary() throws IOException {
    assertCanDeserialize(primaryCodec, compatibilityCodec);
  }

  @Test(expected = IOException.class)
  public void testCompatibilityDoesNotSerializeSecondary() throws IOException {
    assertCanDeserialize(secondaryCodec, compatibilityCodec);
  }

  private void assertCanDeserialize(Codec<TestClass> reader, Codec<TestClass> writer)
      throws IOException {
    assertEquals("foo", deserialize(reader, serialize(writer, t)).data);
  }

  public static class TestClass implements Serializable {
    public String data;
  }

  private static class SimpleCodec implements Codec<TestClass> {

    private final byte firstByte;

    SimpleCodec(char firstByte) {
      this.firstByte = (byte) firstByte;
    }

    @Override
    public TestClass deserialize(InputStream source) throws IOException {
      DataInputStream in = new DataInputStream(source);
      if (in.readByte() != firstByte) {
        throw new IOException("Corrupted stream");
      }
      TestClass t = new TestClass();
      t.data = in.readUTF();
      return t;
    }

    @Override
    public void serialize(TestClass item, OutputStream sink) throws IOException {
      DataOutputStream out = new DataOutputStream(sink);
      out.writeByte(firstByte);
      out.writeUTF(item.data);
    }
  }
}
