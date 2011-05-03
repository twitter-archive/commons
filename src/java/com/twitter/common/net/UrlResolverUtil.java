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

package com.twitter.common.net;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.twitter.common.base.MorePreconditions;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A utility that can resolve HTTP urls.
 *
 * @author John Sirois
 */
class UrlResolverUtil {

  private static final Logger LOG = Logger.getLogger(UrlResolverUtil.class.getName());

  // Default user-agent string to user for HTTP requests.
  private static final String DEFAULT_USER_AGENT = "Lynxy/6.6.6dev.8 libwww-FM/3.14159FM";

  private static Map<String, String> checkNotBlank(Map<String, String> hostToUserAgent) {
    Preconditions.checkNotNull(hostToUserAgent);
    MorePreconditions.checkNotBlank(hostToUserAgent.entrySet());
    return hostToUserAgent;
  }

  private final Function<? super URL, String> urlToUserAgent;

  UrlResolverUtil(Map<String, String> hostToUserAgent) {
    this(Functions.compose(Functions.forMap(checkNotBlank(hostToUserAgent), DEFAULT_USER_AGENT),
        new Function<URL, String>() {
          @Override public String apply(URL url) {
            return url.getHost();
          }
        }));
  }

  UrlResolverUtil(Function<? super URL, String> urlToUserAgent) {
    this.urlToUserAgent = Preconditions.checkNotNull(urlToUserAgent);
  }

  /**
   * Returns the URL that {@code url} lands on, which will be the result of a 3xx redirect,
   * or {@code url} if the url does not redirect using an HTTP 3xx response code.  If there is a
   * non-2xx or 3xx HTTP response code null is returned.
   *
   * @param url The URL to follow.
   * @return The redirected URL, or {@code url} if {@code url} returns a 2XX response, otherwise
   *         null
   * @throws java.io.IOException If an error occurs while trying to follow the url.
   */
  String getEffectiveUrl(String url, @Nullable ProxyConfig proxyConfig) throws IOException {
    Preconditions.checkNotNull(url);
    // Don't follow https.
    if (url.startsWith("https://")) {
      url = url.replace("https://", "http://");
    } else if (!url.startsWith("http://")) {
      url = "http://" + url;
    }

    URL urlObj = new URL(url);

    HttpURLConnection con;
    if (proxyConfig != null) {
      Proxy proxy = new Proxy(Type.HTTP, proxyConfig.getProxyAddress());
      con = (HttpURLConnection) urlObj.openConnection(proxy);
      ProxyAuthorizer.adapt(proxyConfig).authorize(con);
    } else {
      con = (HttpURLConnection) urlObj.openConnection();
    }
    try {

      // TODO(John Sirois): several commonly tweeted hosts 406 or 400 on HEADs and only work with GETs
      // fix the call chain to be able to specify retry-with-GET
      con.setRequestMethod("HEAD");

      con.setUseCaches(true);
      con.setConnectTimeout(5000);
      con.setReadTimeout(5000);
      con.setInstanceFollowRedirects(false);

      // I hate to have to do this, but some URL shorteners don't respond otherwise.
      con.setRequestProperty("User-Agent", urlToUserAgent.apply(urlObj));
      try {
        con.connect();
      } catch (StringIndexOutOfBoundsException e) {
        LOG.info("Got StringIndexOutOfBoundsException when fetching headers for " + url);
        return null;
      }

      int responseCode = con.getResponseCode();
      switch (responseCode / 100) {
        case 2:
          return url;
        case 3:
          String location = con.getHeaderField("Location");
          if (location == null) {
            if (responseCode != 304 /* not modified */) {
              LOG.info(
                  String.format("[%d] Location header was null for URL: %s", responseCode, url));
            }
            return url;
          }

          // HTTP 1.1 spec says this should be an absolute URI, but i see lots of instances where it
          // is relative, so we need to check.
          try {
            String domain = UrlHelper.getDomainChecked(location);
            if (domain == null || domain.isEmpty()) {
              // This is a relative URI.
              location = "http://" + UrlHelper.getDomain(url) + location;
            }
          } catch (URISyntaxException e) {
            LOG.info("location contained an invalid URI: " + location);
          }

          return location;
        default:
          LOG.info("Failed to resolve url: " + url + " with: "
                   + responseCode + " -> " + con.getResponseMessage());
          return null;
      }
    } finally {
      con.disconnect();
    }
  }
}
