package com.twitter.common.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Base64OutputStream;

/**
 * Utility class providing encoding and decoding methods to and from a string to a utf-8 encoded,
 * zlib compressed, Base64 encoded representation of the string. For wider compatibility, the
 * decoder can also automatically recognize GZIP (instead of plain zlib) compressed data too and
 * decode it accordingly.
 *
 * @author Attila Szegedi
 */
public final class Base64ZlibCodec {
  /**
   * Thrown to indicate invalid data while decoding or unzipping.
   *
   * @author Attila Szegedi
   */
  public static class InvalidDataException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidDataException(String message) {
      super(message);
    }

    public InvalidDataException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Text encoding used by the Base64 output stream.
   */
  public static final String BASE64_TEXT_ENCODING = "ASCII";
  private static final int ESTIMATED_PLAINTEXT_TO_ENCODED_RATIO = 4;

  // Prefix all Base64-encoded, zlib compressed data must have
  private static final byte[] ZLIB_HEADER_PREFIX = new byte[] { 120 };
  // Prefix all Base64-encoded, GZIP compressed data must have
  private static final byte[] GZIP_HEADER_PREFIX = new byte[] {31, -117, 8, 0, 0, 0, 0, 0, 0 };
  private static final int DIAGNOSTIC_PREFIX_LENGTH = 16;
  // Text encoding for char-to-byte transformation before compressing a stack trace
  private static final Charset TEXT_ENCODING = com.google.common.base.Charsets.UTF_8;

  private Base64ZlibCodec() {
    // Utility class
  }

  /**
   * Decodes a string. In addition to zlib, it also automatically detects GZIP compressed data and
   * adjusts accordingly.
   *
   * @param encoded the encoded string, represented as a byte array of ASCII-encoded characters
   * @return the decoded string
   * @throws InvalidDataException if the string can not be decoded.
   */
  public static byte[] decode(String encoded) throws InvalidDataException {
    Preconditions.checkNotNull(encoded);
    return decompress(new Base64().decode(encoded));
  }

  private static byte[] decompress(byte[] compressed) throws InvalidDataException {
    byte[] bytes;
    try {
      final InputStream bin = new ByteArrayInputStream(compressed);
      final InputStream zin;
      if (startsWith(compressed, GZIP_HEADER_PREFIX)) {
        zin = new GZIPInputStream(bin);
      } else if (startsWith(compressed, ZLIB_HEADER_PREFIX)) {
        zin = new InflaterInputStream(bin);
      } else {
        throw new Base64ZlibCodec.InvalidDataException("Value doesn't start with either GZIP or zlib header");
      }
      try {
        bytes = ByteStreams.toByteArray(zin);
      } finally {
        zin.close();
      }
    } catch (IOException e) {
      throw new Base64ZlibCodec.InvalidDataException("zlib/GZIP decoding error", e);
    }
    return bytes;
  }

  private static boolean startsWith(byte[] value, byte[] prefix) {
    final int pl = prefix.length;
    if (value.length < pl) {
      return false;
    }
    for (int i = 0; i < pl; ++i) {
      if (value[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Encodes a set of bytes.
   *
   * @param plain the non-encoded bytes
   * @return the encoded string
   */
  public static String encode(byte[] plain) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream(plain.length
        / ESTIMATED_PLAINTEXT_TO_ENCODED_RATIO);
    final OutputStream w = getDeflatingEncodingStream(out);
    try {
      w.write(plain);
      w.close();
      return out.toString(BASE64_TEXT_ENCODING);
    } catch (UnsupportedEncodingException e) {
      throw reportUnsupportedEncoding();
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private static OutputStream getDeflatingEncodingStream(OutputStream out) {
    return new DeflaterOutputStream(new Base64OutputStream(out, true,
        Integer.MAX_VALUE, null));
  }

  /**
   * Returns a writer that writes through to the specified output stream, utf-8 encoding,
   * zlib compressing, and Base64 encoding its input along the way.
   *
   * @param out the output stream that receives the final output
   * @return a writer for the input
   */
  public static Writer getEncodingWriter(OutputStream out) {
    return new OutputStreamWriter(getDeflatingEncodingStream(out), TEXT_ENCODING);
  }

  private static AssertionError reportUnsupportedEncoding() {
    return new AssertionError(String.format("JVM doesn't support the %s encoding", TEXT_ENCODING));
  }
}