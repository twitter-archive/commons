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
import com.twitter.common.base.Closure;
import com.twitter.common.net.monitoring.TrafficMonitor;
import org.antlr.stringtemplate.StringTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Servlet to display live information about registered thrift clients and servers.
 *
 * @author William Farner
 */
public class ThriftServlet extends StringTemplateServlet {

  /**
   * {@literal @Named} binding key for client monitor.
   */
  public static final String THRIFT_CLIENT_MONITORS =
      "com.twitter.common.net.http.handlers.ThriftServlet.THRIFT_CLIENT_MONITORS";

  /**
   * {@literal @Named} binding key for server monitor.
   */
  public static final String THRIFT_SERVER_MONITORS =
      "com.twitter.common.net.http.handlers.ThriftServlet.THRIFT_SERVER_MONITORS";

  private Set<TrafficMonitor> clientMonitors;
  private Set<TrafficMonitor> serverMonitors;

  @Inject
  public ThriftServlet(
      @Named(ThriftServlet.THRIFT_CLIENT_MONITORS) Set<TrafficMonitor> clientMonitors,
      @Named(ThriftServlet.THRIFT_SERVER_MONITORS) Set<TrafficMonitor> serverMonitors) {
    super("thrift", true);
    this.clientMonitors = Preconditions.checkNotNull(clientMonitors);
    this.serverMonitors = Preconditions.checkNotNull(serverMonitors);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    writeTemplate(resp, new Closure<StringTemplate>() {
      @Override public void execute(StringTemplate template) {
        template.setAttribute("clientMonitors", clientMonitors);
        template.setAttribute("serverMonitors", serverMonitors);
      }
    });
  }
}
