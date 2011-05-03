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

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.twitter.common.base.ExceptionalSupplier;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A servlet that provides a crude mechanism for monitoring a service's health.  If the servlet
 * returns {@link #IS_HEALTHY} then the containing service should be deemed healthy.
 *
 * @author John Sirois
 */
public class HealthHandler extends HttpServlet {

  /**
   * A {@literal @Named} binding key for the Healthz servlet health checker.
   */
  public static final String HEALTH_CHECKER_KEY =
      "com.twitter.common.net.http.handlers.Healthz.checker";

  /**
   * The plain text response string this servlet returns in the body of its responses to health
   * check requests when its containing service is healthy.
   */
  public static final String IS_HEALTHY = "OK";

  private static final String IS_NOT_HEALTHY = "SICK";

  private static final Logger LOG = Logger.getLogger(HealthHandler.class.getName());

  private final ExceptionalSupplier<Boolean, ?> healthChecker;

  /**
   * Constructs a new Healthz that uses the given {@code healthChecker} to determine current health
   * of the service for at the point in time of each GET request.  The given {@code healthChecker}
   * should return {@code true} if the service is healthy and {@code false} otherwise.  If the
   * {@code healthChecker} returns null or throws the service is considered unhealthy.
   *
   * @param healthChecker a supplier that is called to perform a health check
   */
  @Inject
  public HealthHandler(@Named(HEALTH_CHECKER_KEY) ExceptionalSupplier<Boolean, ?> healthChecker) {
    this.healthChecker = Preconditions.checkNotNull(healthChecker);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    resp.setContentType("text/plain");
    PrintWriter writer = resp.getWriter();
    try {
      writer.println(Boolean.TRUE.equals(healthChecker.get()) ? IS_HEALTHY : IS_NOT_HEALTHY);
    } catch (Exception e) {
      writer.println(IS_NOT_HEALTHY);
      LOG.log(Level.WARNING, "Health check failed.", e);
    }
  }
}
