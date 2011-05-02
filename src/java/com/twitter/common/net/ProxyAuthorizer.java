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

import org.apache.commons.codec.binary.Base64;

import java.net.HttpURLConnection;

/**
 * Authorizes http connection for use over the proxy it is built with
 *
 * @author William Farner
 */
public class ProxyAuthorizer {
  private final ProxyConfig config;

  private ProxyAuthorizer(ProxyConfig config) {
    this.config = config;
  }

  public static ProxyAuthorizer adapt(ProxyConfig config) {
    return new ProxyAuthorizer(config);
  }

  public void authorize(HttpURLConnection httpCon) {
    httpCon.setRequestProperty("Proxy-Authorization", "Basic " +
        new String(Base64.encodeBase64(new String(config.getProxyUser() + ":" +
          config.getProxyPassword()).getBytes())).trim());
  }
}
