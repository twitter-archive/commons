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

package com.twitter.common.net.http;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.twitter.common.net.http.handlers.TextResponseHandler;
import org.mortbay.jetty.AbstractConnector;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple multi-threaded HTTP server dispatcher.  Supports registering handlers for different
 * URI paths, which will be called when a request is received.
 *
 * @author William Farner
 */
public class HttpServerDispatch {
  private static final Logger LOG = Logger.getLogger(HttpServerDispatch.class.getName());

  // Registered handlers. Used only for display.
  private final Set<String> registeredHandlers = Sets.newHashSet();

  private Server server;
  private Context context;
  private int port;

  /**
   * Opens the HTTP server on the given port.
   *
   * @param port The port to listen on.
   * @return {@code true} if the server started successfully on the port, {@code false} otherwise.
   */
  public boolean listen(int port) {
    return listen(port, port);
  }

  /**
   * Opens the HTTP server on random port within the given range.
   *
   * @param minPort The minimum port number to listen on.
   * @param maxPort The maximum port number to listen on.
   * @return {@code true} if the server started successfully on the port, {@code false} otherwise.
   */
  public synchronized boolean listen(int minPort, int maxPort) {
    boolean state = !isStarted();
    Preconditions.checkState(state,
        "HttpServerDispatch has already been started on port: %d", port);

    Connector connector = openConnector(minPort, maxPort);
    if (connector == null) return false; // Couldn't open a server port.
    port = connector.getLocalPort();

    server = new Server();
    server.addConnector(connector);
    context = new Context(server, "/", Context.NO_SESSIONS);
    context.addServlet(new ServletHolder(new RootHandler()), "/");

    try {
      server.start();
      LOG.info("HTTP server is listening on port " + port);
      return true;
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "HTTP server failed to start on port " + connector.getLocalPort(), e);
      return false;
    }
  }

  public synchronized boolean isStarted() {
    return (server != null) && server.isStarted();
  }

  public synchronized int getPort() {
    Preconditions.checkState(isStarted(), "HttpServer must be started before port can be determined");
    return port;
  }

  private Connector openConnector(int minPort, int maxPort) {
    if (minPort != 0 || maxPort != 0) {
      Preconditions.checkState(minPort > 0, "Invalid port range.");
      Preconditions.checkState(maxPort > 0, "Invalid port range.");
      Preconditions.checkState(minPort <= maxPort, "Invalid port range.");
    }
    int attempts = 0;
    int port;

    int maxAttempts = minPort == maxPort ? 1 : 5;
    while (++attempts <= maxAttempts) {
      if (minPort == maxPort) {
        port = minPort;
      } else {
        port = minPort + new Random().nextInt(maxPort - minPort);
      }
      LOG.info("Attempting to listen on port " + port);

      try {
        // TODO(John Sirois): consider making Connector impl parametrizable
        AbstractConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        // Create the server with a maximum TCP backlog of 50, meaning that when the request queue
        // exceeds 50, subsequent connections may be rejected.
        connector.setAcceptQueueSize(50);
        connector.open();
        return connector;
      } catch (IOException e) {
        LOG.log(Level.INFO, "Failed to create HTTP server on port " + port);
      }
    }
    return null;
  }

  /**
   * Stops the HTTP server.
   */
  public synchronized void stop() {
    if (isStarted()) {
      try {
        server.stop();
      } catch (Exception e) {
        LOG.log(Level.SEVERE, "Error stopping HTTPServer on " + port, e);
      }
    }
  }

  /**
   * Registers a URI handler, replacing the existing handler if it exists.
   *
   * @param path The URI path that the handler should be called for.
   * @param handler The handler to call.
   * @param initParams An optional map of servlet init parameter names and their values.
   * @param silent Whether to display the registered handler in the root "/" response.
   *               Useful for handlers that you want to avoid accidental clicks on.
   */
  public synchronized void registerHandler(String path, HttpServlet handler,
      @Nullable Map<String, String> initParams, boolean silent) {
    Preconditions.checkNotNull(path);
    Preconditions.checkNotNull(handler);
    Preconditions.checkState(path.length() > 0);
    Preconditions.checkState(path.charAt(0) == '/');

    if (silent) {
      registeredHandlers.remove(path);
    } else {
      registeredHandlers.add(path);
    }

    ServletHolder servletHolder = new ServletHolder(handler);
    if (initParams != null) {
      servletHolder.setInitParameters(initParams);
    }
    context.addServlet(servletHolder, path.replaceFirst("/?$", "/*"));
  }

  public Context getRootContext() {
    Preconditions.checkState(context != null, "Context is not yet available. " +
        "Ensure that listen(...) is called prior to calling this method.");
    return context;
  }

  /**
   * The root handler, which will display the paths at which all handlers are registered.
   */
  private class RootHandler extends TextResponseHandler {
    public RootHandler() {
      super("text/html");
    }

    @Override
    public Iterable<String> getLines(HttpServletRequest request) {
      List<String> lines = Lists.newArrayList();
      lines.add("<html>");
      for (String handler : registeredHandlers) {
        lines.add(String.format("<a href='%s'>%s</a><br />", handler, handler));
      }
      lines.add("</html>");
      return lines;
    }
  }
}
