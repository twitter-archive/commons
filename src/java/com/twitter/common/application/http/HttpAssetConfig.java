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

package com.twitter.common.application.http;

import java.net.URL;

import com.google.common.io.Resources;

import com.twitter.common.net.http.handlers.AssetHandler;
import com.twitter.common.net.http.handlers.AssetHandler.StaticAsset;

import static com.twitter.common.base.MorePreconditions.checkNotBlank;

/**
 * Configuration for a static HTTP-served asset.
 *
 * TODO(William Farner): Move this to a more appropriate package after initial AppLauncher check-in.
 *
 * @author William Farner
 */
public class HttpAssetConfig {
  public final String path;
  public final AssetHandler handler;
  public final boolean silent;

  /**
   * Creates a new asset configuration.
   *
   * @param path HTTP path the asset should be accessible from.
   * @param asset Asset resource URL.
   * @param contentType HTTP content-type to report for the asset.
   * @param silent Whether the asset should be visible on the default index page.
   */
  public HttpAssetConfig(String path, URL asset, String contentType, boolean silent) {
    this.path = checkNotBlank(path);
    this.handler = new AssetHandler(
        new StaticAsset(Resources.newInputStreamSupplier(asset), contentType, true));
    this.silent = silent;
  }
}
