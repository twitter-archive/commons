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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.InputSupplier;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Servlet that is responsible for serving an asset.
 *
 * @author William Farner
 */
public class AssetHandler extends HttpServlet {

  @VisibleForTesting
  static final Amount<Integer, Time> CACHE_CONTROL_MAX_AGE_SECS = Amount.of(30, Time.DAYS);
  private static final String GZIP_ENCODING = "gzip";

  private final StaticAsset staticAsset;

  public static class StaticAsset {
    private final InputSupplier<? extends InputStream> inputSupplier;
    private final String contentType;
    private final boolean cacheLocally;

    private byte[] gzipData = null;
    private String hash = null;

    /**
     * Creates a new static asset.
     *
     * @param inputSupplier Supplier of the input stream from which to load the asset.
     * @param contentType HTTP content type of the asset.
     * @param cacheLocally If {@code true} the asset will be loaded once and stored in memory, if
     *    {@code false} it will be loaded on each request.
     */
    public StaticAsset(InputSupplier<? extends InputStream> inputSupplier,
        String contentType, boolean cacheLocally) {
      this.inputSupplier = checkNotNull(inputSupplier);
      this.contentType = checkNotNull(contentType);
      this.cacheLocally = cacheLocally;
    }

    public String getContentType() {
      return contentType;
    }

    public synchronized byte[] getRawData() throws IOException {
      byte[] zipData = getGzipData();
      GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(zipData));
      return ByteStreams.toByteArray(in);
    }

    public synchronized byte[] getGzipData() throws IOException {
      byte[] data = gzipData;
      // Ensure we don't double-read after a call to getChecksum().
      if (!cacheLocally || gzipData == null) {
        load();
        data = gzipData;
      }
      if (!cacheLocally) {
        gzipData = null;
      }

      return data;
    }

    public synchronized String getChecksum() throws IOException {
      if (hash == null) {
        load();
      }
      return hash;
    }

    private void load() throws IOException {
      ByteArrayOutputStream gzipBaos = new ByteArrayOutputStream();
      GZIPOutputStream gzipStream = new GZIPOutputStream(gzipBaos);
      ByteStreams.copy(inputSupplier, gzipStream);
      gzipStream.flush();  // copy() does not flush or close output stream.
      gzipStream.close();
      gzipData = gzipBaos.toByteArray();

      // Calculate a checksum of the gzipped data.
      hash = Base64.encodeBase64String(DigestUtils.md5(gzipData)).trim();
    }
  }

  /**
   * Creates a new asset handler.
   *
   * @param staticAsset The asset to serve.
   */
  public AssetHandler(StaticAsset staticAsset) {
    this.staticAsset = checkNotNull(staticAsset);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    OutputStream responseBody = resp.getOutputStream();

    if (checksumMatches(req)) {
      resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    } else {
      setPayloadHeaders(resp);

      boolean gzip = supportsGzip(req);
      if (gzip) {
        resp.setHeader("Content-Encoding", GZIP_ENCODING);
      }

      InputStream in = new ByteArrayInputStream(
          gzip ? staticAsset.getGzipData() : staticAsset.getRawData());
      ByteStreams.copy(in, responseBody);
    }

    Closeables.close(responseBody, /* swallowIOException */ true);
  }

  private void setPayloadHeaders(HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType(staticAsset.getContentType());
    resp.setHeader("Cache-Control", "public,max-age=" + CACHE_CONTROL_MAX_AGE_SECS);

    String checksum = staticAsset.getChecksum();
    if (checksum != null) {
      resp.setHeader("ETag", checksum);
    }
  }

  private boolean checksumMatches(HttpServletRequest req) throws IOException {
    // TODO(William Farner): Change this to more fully comply with
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.26
    // Specifically - a response to 'If-None-Match: *' should include ETag as well as other
    // cache-related headers.
    String suppliedETag = req.getHeader("If-None-Match");
    if ("*".equals(suppliedETag)) {
      return true;
    }

    String checksum = staticAsset.getChecksum();
    // Note - this isn't a completely accurate check since the tag we end up matching against could
    // theoretically be the actual tag with some extra characters appended.
    return (checksum != null) && (suppliedETag != null) && suppliedETag.contains(checksum);
  }

  private static boolean supportsGzip(HttpServletRequest req) {
    String header = req.getHeader("Accept-Encoding");
    return (header != null)
        && Iterables.contains(Splitter.on(",").trimResults().split(header), GZIP_ENCODING);
  }
}
