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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author William Farner
 */
public class UrlHelper {

  private static final Logger LOG = Logger.getLogger(UrlHelper.class.getName());

  /**
   * Gets the domain from {@code url}.
   *
   * @param url A url.
   * @return The domain portion of the URL, or {@code null} if the url is invalid.
   */
  public static String getDomain(String url) {
    try {
      return getDomainChecked(url);
    } catch (URISyntaxException e) {
      LOG.finest("Malformed url: " + url);
      return null;
    }
  }

  /**
   * Gets the domain from {@code uri}, and throws an exception if it's not a valid uri.
   *
   * @param url A url.
   * @throws URISyntaxException if url is not a valid {@code URI}
   * @return The domain portion of the given url, or {@code null} if the host is undefined.
   */
  public static String getDomainChecked(String url) throws URISyntaxException {
    Preconditions.checkNotNull(url);
    url = addProtocol(url);
    return new URI(url).getHost();
  }

  /**
   * Gets the path from {@code url}.
   *
   * @param url A url.
   * @return The path portion of the URL, or {@code null} if the url is invalid.
   */
  public static String getPath(String url) {
    Preconditions.checkNotNull(url);
    url = addProtocol(url);
    try {
      return new URI(url).getPath();
    } catch (URISyntaxException e) {
      LOG.info("Malformed url: " + url);
      return null;
    }
  }

  /**
   * Strips URL parameters from a url.
   * This will remove anything after and including a question mark in the URL.
   *
   * @param url The URL to strip parameters from.
   * @return The original URL with parameters stripped, which will be the original URL if no
   *   parameters were found.
   */
  public static String stripUrlParameters(String url) {
    Preconditions.checkNotNull(url);
    int paramStartIndex = url.indexOf("?");
    if (paramStartIndex == -1) {
      return url;
    } else {
      return url.substring(0, paramStartIndex);
    }
  }

  /**
   * Convenience method that calls #stripUrlParameters(String) for a URL.
   *
   * @param url The URL to strip parameters from.
   * @return The original URL with parameters stripped, which will be the original URL if no
   *   parameters were found.
   */
  public static String stripUrlParameters(URL url) {
    return stripUrlParameters(url.toString());
  }

  private static final Pattern URL_PROTOCOL_REGEX =
      Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

  /**
   * Checks whether a URL specifies its protocol, prepending http if it does not.
   *
   * @param url The URL to fix.
   * @return The URL with the http protocol specified if no protocol was already specified.
   */
  public static String addProtocol(String url) {
    Preconditions.checkNotNull(url);
    Matcher matcher = URL_PROTOCOL_REGEX.matcher(url);
    if (!matcher.find()) {
      url = "http://" + url;
    }
    return url;
  }

  /**
   * Gets the domain levels for a host.
   * For example, sub1.sub2.domain.co.uk would return
   * [sub1.sub2.domain.co.uk, sub2.domain.co.uk, domain.co.uk, co.uk, uk].
   *
   *
   * @param host The host to peel subdomains off from.
   * @return The domain levels in this host.
   */
  public static List<String> getDomainLevels(String host) {
    Preconditions.checkNotNull(host);

    // Automatically include www prefix if not present.
    if (!host.startsWith("www")) {
      host = "www." + host;
    }

    Joiner joiner = Joiner.on(".");
    List<String> domainParts = Lists.newLinkedList(Arrays.asList(host.split("\\.")));
    List<String> levels = Lists.newLinkedList();

    while (!domainParts.isEmpty()) {
      levels.add(joiner.join(domainParts));
      domainParts.remove(0);
    }

    return levels;
  }
}
