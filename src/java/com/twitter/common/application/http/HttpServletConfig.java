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

import javax.servlet.http.HttpServlet;

import com.google.common.collect.ImmutableMap;

import static com.google.common.base.Preconditions.checkNotNull;

import static com.twitter.common.base.MorePreconditions.checkNotBlank;

/**
 * An {@link javax.servlet.http.HttpServlet} configuration used to mount HTTP handlers via
 * {@link Registration#registerServlet(com.google.inject.Binder, HttpServletConfig)}.
 *
 * TODO(William Farner): Move this to a more appropriate package after initial AppLauncher check-in.
 *
 */
public class HttpServletConfig {
  public final String path;
  public final Class<? extends HttpServlet> handlerClass;
  public final ImmutableMap<String, String> params;
  public final boolean silent;

  /**
   * Creates a new servlet config.
   *
   * @param path the absolute path to mount the handler on
   * @param servletClass the type of servlet that will render pages at {@code path}
   * @param silent whether or not to display a link for this handler on the landing page
   */
  public HttpServletConfig(String path, Class<? extends HttpServlet> servletClass,
      boolean silent) {
    this(path, servletClass, ImmutableMap.<String, String>of(), silent);
  }

  /**
   * Registers a new servlet config with servlet initialization parameters.
   *
   * @param path the absolute path to mount the handler on
   * @param servletClass the type of servlet that will render pages at {@code path}
   * @param params a map of servlet init parameters to initialize the servlet with
   * @param silent whether or not to display a link for this handler on the landing page
   */
  public HttpServletConfig(String path, Class<? extends HttpServlet> servletClass,
      ImmutableMap<String, String> params, boolean silent) {

    this.path = checkNotBlank(path);
    this.handlerClass = checkNotNull(servletClass);
    this.params = checkNotNull(params);
    this.silent = silent;
  }
}
