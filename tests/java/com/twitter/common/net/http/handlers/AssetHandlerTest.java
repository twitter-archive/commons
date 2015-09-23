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

package com.twitter.common.net.http.handlers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.net.http.handlers.AssetHandler.StaticAsset;
import com.twitter.common.testing.easymock.EasyMockTest;

import static com.twitter.common.net.http.handlers.AssetHandler.CACHE_CONTROL_MAX_AGE_SECS;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.easymock.EasyMock.expect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class AssetHandlerTest extends EasyMockTest {

  private static final String TEST_DATA = "here is my great test data";
  // Checksum of the gzipped TEST_DATA.
  private static final String TEST_DATA_CHECKSUM = "ePvVhtAeVRu85KSOLKL0oQ==";
  private static final String CONTENT_TYPE = "text/plain";

  private InputSupplier<InputStream> inputSupplier;

  @Before
  public void setUp() {
    inputSupplier = createMock(new Clazz<InputSupplier<InputStream>>() {});
  }

  private static class Request {
    private final HttpServletRequest req;
    private final HttpServletResponse resp;
    private final ByteArrayOutputStream responseBody;

    Request(HttpServletRequest req, HttpServletResponse resp, ByteArrayOutputStream responseBody) {
      this.req = req;
      this.resp = resp;
      this.responseBody = responseBody;
    }
  }

  private Request doGet(String suppliedChecksum, String supportedEncodings,
      int expectedResponseCode, boolean expectRead) throws Exception {
    HttpServletRequest req = createMock(HttpServletRequest.class);
    HttpServletResponse resp = createMock(HttpServletResponse.class);

    if (expectRead) {
      expect(inputSupplier.getInput()).andReturn(new ByteArrayInputStream(TEST_DATA.getBytes()));
    }

    expect(req.getHeader("If-None-Match")).andReturn(suppliedChecksum);

    resp.setStatus(expectedResponseCode);
    if (expectedResponseCode == SC_OK) {
      expect(req.getHeader("Accept-Encoding")).andReturn(supportedEncodings);
      resp.setHeader("Cache-Control", "public,max-age=" + CACHE_CONTROL_MAX_AGE_SECS);
      resp.setHeader("ETag", TEST_DATA_CHECKSUM);
      resp.setContentType(CONTENT_TYPE);

      if (supportedEncodings != null && supportedEncodings.contains("gzip")) {
        resp.setHeader("Content-Encoding", "gzip");
      }
    }
    return new Request(req, resp, expectPayload(resp));
  }

  @Test
  public void testCached() throws Exception {

    // First request - no cached value
    Request test1 = doGet(
        null,  // No local checksum.
        null,  // No encodings supported.
        SC_OK,
        true   // Triggers a data read.
    );

    // Second request - client performs conditional GET with wrong checksum.
    Request test2 = doGet(
        "foo", // Wrong checksum.
        null,  // No encodings supported.
        SC_OK,
        false   // No read.
    );

    // Third request - client performs conditional GET with correct checksum.
    Request test3 = doGet(
        TEST_DATA_CHECKSUM,  // Correct checksum.
        null,  // No encodings supported.
        SC_NOT_MODIFIED,
        false   // No read.
    );

    control.replay();

    AssetHandler handler = new AssetHandler(new StaticAsset(inputSupplier, CONTENT_TYPE, true));

    handler.doGet(test1.req, test1.resp);
    assertThat(new String(test1.responseBody.toByteArray()), is(TEST_DATA));

    handler.doGet(test2.req, test2.resp);
    assertThat(new String(test2.responseBody.toByteArray()), is(TEST_DATA));

    handler.doGet(test3.req, test3.resp);
    assertThat(new String(test3.responseBody.toByteArray()), is(""));
  }

  @Test
  public void testCachedGzipped() throws Exception {

    // First request - no cached value
    Request test1 = doGet(
        null,  // No local checksum.
        "gzip",  // Supported encodings.
        SC_OK,
        true   // Triggers a data read.
    );

    // Second request - client performs conditional GET with wrong checksum.
    Request test2 = doGet(
        "foo", // Wrong checksum.
        "gzip,fakeencoding",  // Supported encodings.
        SC_OK,
        false   // No read.
    );

    // Third request - client performs conditional GET with correct checksum.
    Request test3 = doGet(
        TEST_DATA_CHECKSUM,  // Correct checksum.
        "gzip,deflate",  // Supported encodings.
        SC_NOT_MODIFIED,
        false   // No read.
    );

    control.replay();

    AssetHandler handler = new AssetHandler(new StaticAsset(inputSupplier, CONTENT_TYPE, true));

    handler.doGet(test1.req, test1.resp);
    assertThat(unzip(test1.responseBody), is(TEST_DATA));

    handler.doGet(test2.req, test2.resp);
    assertThat(unzip(test2.responseBody), is(TEST_DATA));

    handler.doGet(test3.req, test3.resp);
    assertThat(new String(test3.responseBody.toByteArray()), is(""));
  }

  @Test
  public void testUncached() throws Exception {

    // First request - no cached value
    Request test1 = doGet(
        null,  // No local checksum.
        null,  // No encodings supported.
        SC_OK,
        true   // Triggers a data read.
    );

    // Second request - client performs conditional GET with wrong checksum.
    Request test2 = doGet(
        "foo", // Wrong checksum.
        null,  // No encodings supported.
        SC_OK,
        true   // Triggers a data read.
    );

    // Third request - client performs conditional GET with correct checksum.
    Request test3 = doGet(
        TEST_DATA_CHECKSUM,  // Correct checksum.
        null,  // No encodings supported.
        SC_NOT_MODIFIED,
        true   // Triggers a data read.
    );

    control.replay();

    AssetHandler handler = new AssetHandler(new StaticAsset(inputSupplier, CONTENT_TYPE, false));

    handler.doGet(test1.req, test1.resp);
    assertThat(new String(test1.responseBody.toByteArray()), is(TEST_DATA));

    handler.doGet(test2.req, test2.resp);
    assertThat(new String(test2.responseBody.toByteArray()), is(TEST_DATA));

    handler.doGet(test3.req, test3.resp);
    assertThat(new String(test3.responseBody.toByteArray()), is(""));
  }

  @Test
  public void testUncachedGzipped() throws Exception {

    // First request - no cached value
    Request test1 = doGet(
        null,  // No local checksum.
        "gzip",  // Supported encodings.
        SC_OK,
        true   // Triggers a data read.
    );

    // Second request - client performs conditional GET with wrong checksum.
    Request test2 = doGet(
        "foo", // Wrong checksum.
        "gzip,fakeencoding",  // Supported encodings.
        SC_OK,
        true   // Triggers a data read.
    );

    // Third request - client performs conditional GET with correct checksum.
    Request test3 = doGet(
        TEST_DATA_CHECKSUM,  // Correct checksum.
        "gzip,deflate",  // Supported encodings.
        SC_NOT_MODIFIED,
        true   // Triggers a data read.
    );

    control.replay();

    AssetHandler handler = new AssetHandler(new StaticAsset(inputSupplier, CONTENT_TYPE, false));

    handler.doGet(test1.req, test1.resp);
    assertThat(unzip(test1.responseBody), is(TEST_DATA));

    handler.doGet(test2.req, test2.resp);
    assertThat(unzip(test2.responseBody), is(TEST_DATA));

    handler.doGet(test3.req, test3.resp);
    assertThat(new String(test3.responseBody.toByteArray()), is(""));
  }

  private static ByteArrayOutputStream expectPayload(HttpServletResponse resp) throws Exception {
    ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
    expect(resp.getOutputStream()).andReturn(new FakeServletOutputStream(responseBody));
    return responseBody;
  }

  private static String unzip(ByteArrayOutputStream streamData) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(streamData.toByteArray());
    GZIPInputStream unzip = new GZIPInputStream(in);
    return new String(ByteStreams.toByteArray(unzip));
  }

  private static class FakeServletOutputStream extends ServletOutputStream {
    private final OutputStream realStream;

    FakeServletOutputStream(OutputStream realStream) {
      this.realStream = realStream;
    }

    @Override
    public void write(int b) throws IOException {
      realStream.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      realStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      realStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      realStream.flush();
    }

    @Override
    public void close() throws IOException {
      realStream.close();
    }

    @Override
    public void print(String s) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void print(boolean b) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void print(char c) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void print(int i) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void print(long l) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void print(float f) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void print(double d) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void println() throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void println(String s) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void println(boolean b) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void println(char c) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void println(int i) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void println(long l) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void println(float f) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void println(double d) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
}
