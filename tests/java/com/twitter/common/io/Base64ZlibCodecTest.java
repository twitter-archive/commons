package com.twitter.common.io;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.junit.Assert;

import com.twitter.common.io.Base64ZlibCodec.InvalidDataException;

import junit.framework.TestCase;

public class Base64ZlibCodecTest extends TestCase {

  public void testEncodeDecode() throws Exception {
    testEncodeDecode(0);
    for (int i = 1; i < 10000; i *= 10) {
      testEncodeDecode(i * 1024);
    }
  }

  public void testDecodeGzip() throws Exception {
    final byte[] input = createRandomBytes(10240);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    final OutputStream os = new GZIPOutputStream(new Base64OutputStream(out));
    os.write(input);
    os.close();
    final String encoded = new String(out.toByteArray(), "8859_1");
    assertTrue(encoded.startsWith("H4sIAAAAAAAAA"));
    Assert.assertArrayEquals(input, Base64ZlibCodec.decode(encoded));
  }

  public void testInvalidData() throws Exception {
    final String plain = createRandomText(10240);
    try {
      Base64ZlibCodec.decode("this is invalid");
      fail();
    } catch (InvalidDataException e) {
      // This is expected
    }
  }

  public void testCorruptedData() throws Exception {
    final char[] encoded = Base64ZlibCodec.encode(createRandomBytes(1024)).toCharArray();
    for (int i = 100; i < encoded.length; ++i) {
      if (encoded[i] != 'Z') {
        ++encoded[i];
        break;
      }
    }
    try {
      Base64ZlibCodec.decode(new String(encoded));
      fail();
    } catch (InvalidDataException e) {
      // This is expected
    }
  }

  private static void testEncodeDecode(int len) throws Exception {
    final byte[] input = createRandomBytes(len);
    final String encoded = Base64ZlibCodec.encode(input);
    assertTrue(encoded.startsWith("eJ"));
    Assert.assertArrayEquals(input, Base64ZlibCodec.decode(encoded));
  }

  private static String createRandomText(int len) throws UnsupportedEncodingException {
    final byte[] msg = new byte[len];
    new Random().nextBytes(msg);
    return new String(msg, "8859_1");
  }

  private static byte[] createRandomBytes(int len) throws UnsupportedEncodingException {
    final byte[] msg = new byte[len];
    new Random().nextBytes(msg);
    return msg;
  }
}
