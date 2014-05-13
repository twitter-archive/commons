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

package com.twitter.common.text.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import org.junit.Test;

import com.twitter.common.text.DefaultTextTokenizer;
import com.twitter.common.text.TextTokenizer;
import com.twitter.common.text.token.TwitterTokenStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenStreamSerializerTest {

  @Test
  public void testSerialization() throws Exception {
    final String text = "Hello, this is a test";

    TextTokenizer tokenizer = new DefaultTextTokenizer.Builder()
        .setKeepPunctuation(false)
        .build();

    TwitterTokenStream stream = tokenizer.getDefaultTokenStream();
    stream.reset(text);

    TokenStreamSerializer serializer = TokenStreamSerializer.builder()
        .add(new CharSequenceTermAttributeSerializer())
        .add(new TokenTypeAttributeSerializer())
        .add(new PositionIncrementAttributeSerializer())
        .build();

    byte[] data  = serializer.serialize(stream);

    TwitterTokenStream deserialized = serializer.deserialize(data, text);

    for (int i = 0; i < 2; ++i) {
      // run this twice so that we see that resetting we still get the same tokens.
      stream.reset(text);
      while (stream.incrementToken()) {
        assertTrue(deserialized.incrementToken());
        assertEquals(stream.reflectAsString(true), deserialized.reflectAsString(true));
      }
      assertFalse(deserialized.incrementToken());
      deserialized.reset(null);
    }
  }

  @Test
  public void testVInt() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    TokenStreamSerializer.AttributeOutputStream out =
        new TokenStreamSerializer.AttributeOutputStream(baos);

    final int N = 10000;
    final int[] expected = new int[N];

    Random rnd = new Random();
    for (int i = 0; i < N; i++) {
      expected[i] = rnd.nextInt();
      out.writeVInt(expected[i]);
    }

    out.flush();
    byte[] data = baos.toByteArray();

    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    TokenStreamSerializer.AttributeInputStream in =
        new TokenStreamSerializer.AttributeInputStream(bais);

    for (int i = 0; i < N; i++) {
      assertEquals(expected[i], in.readVInt());
    }
  }

  @Test
  public void testVLong() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    TokenStreamSerializer.AttributeOutputStream out =
        new TokenStreamSerializer.AttributeOutputStream(baos);

    final int N = 100000;
    final long[] expected = new long[N];

    Random rnd = new Random();
    for (int i = 0; i < N; i++) {
      expected[i] = rnd.nextLong();
      out.writeVLong(expected[i]);
    }

    out.flush();
    byte[] data = baos.toByteArray();

    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    TokenStreamSerializer.AttributeInputStream in =
        new TokenStreamSerializer.AttributeInputStream(bais);

    for (int i = 0; i < N; i++) {
      assertEquals(expected[i], in.readVLong());
    }
  }

  @Test
  public void testIncompatibleStreams() throws Exception {
    final String text = "Test that incompatible streams are - actually -incompatible.";

    TextTokenizer tokenizer = new DefaultTextTokenizer.Builder()
        .setKeepPunctuation(false)
        .build();

    TwitterTokenStream stream = tokenizer.getDefaultTokenStream();
    stream.reset(text);

    TokenStreamSerializer serializer = TokenStreamSerializer.builder()
        .add(new CharSequenceTermAttributeSerializer())
        .add(new TokenTypeAttributeSerializer())
        .add(new PositionIncrementAttributeSerializer())
        .build();

    byte[] data  = serializer.serialize(stream);

    // Notice that I just flipped two serializers.
    TokenStreamSerializer incompatibleSerializer = TokenStreamSerializer.builder()
        .add(new CharSequenceTermAttributeSerializer())
        .add(new PositionIncrementAttributeSerializer())
        .add(new TokenTypeAttributeSerializer())
        .build();

    boolean exceptionWasThrown = false;
    try {
      incompatibleSerializer.deserialize(data, text);
    } catch (TokenStreamSerializer.VersionMismatchException e) {
      exceptionWasThrown = true;
    }
    assertTrue("The expected exception was not thrown!", exceptionWasThrown);
  }
}
