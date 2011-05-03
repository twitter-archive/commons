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

import javax.servlet.http.HttpServlet;

import com.google.common.io.Resources;
import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

/**
 * Utility class for registering HTTP servlets and assets.
 *
 * @author William Farner
 */
public class Registration {

  private Registration() {
    // Utility class.
  }

  /**
   * Equivalent to
   * {@code registerServlet(binder, new HttpServletConfig(path, servletClass, silent))}.
   */
  public static void registerServlet(Binder binder, String path,
      Class<? extends HttpServlet> servletClass, boolean silent) {
    registerServlet(binder, new HttpServletConfig(path, servletClass, silent));
  }

  /**
   * Registers a binding for an {@link javax.servlet.http.HttpServlet} to be exported at a specified
   * path.
   *
   * @param binder a guice binder to register the handler with
   * @param config a servlet mounting specification
   */
  public static void registerServlet(Binder binder, HttpServletConfig config) {
    Multibinder.newSetBinder(binder, HttpServletConfig.class).addBinding().toInstance(config);
  }

  /**
   * Registers a binding for a URL asset to be served by the HTTP server, with an optional
   * entity tag for cache control.
   *
   * @param binder a guice binder to register the handler with
   * @param servedPath Path to serve the resource from in the HTTP server.
   * @param asset Resource to be served.
   * @param assetType MIME-type for the asset.
   * @param silent Whether the server should hide this asset on the index page.
   */
  public static void registerHttpAsset(Binder binder, String servedPath, URL asset,
      String assetType, boolean silent) {
    Multibinder.newSetBinder(binder, HttpAssetConfig.class).addBinding().toInstance(
        new HttpAssetConfig(servedPath, asset, assetType, silent));
  }

  /**
   * Registers a binding for a classpath resource to be served by the HTTP server, using a resource
   * path relative to a class.
   *
   * @param binder a guice binder to register the handler with
   * @param servedPath Path to serve the asset from in the HTTP server.
   * @param contextClass Context class for defining the relative path to the asset.
   * @param assetRelativePath Path to the served asset, relative to {@code contextClass}.
   * @param assetType MIME-type for the asset.
   * @param silent Whether the server should hide this asset on the index page.
   */
  public static void registerHttpAsset(Binder binder, String servedPath, Class<?> contextClass,
      String assetRelativePath, String assetType, boolean silent) {
    registerHttpAsset(binder, servedPath, Resources.getResource(contextClass, assetRelativePath),
        assetType, silent);
  }
}
