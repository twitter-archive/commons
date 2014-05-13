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

import java.io.IOException;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import org.mortbay.jetty.AbstractConnector;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.RequestLog;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.RequestLogHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import com.twitter.common.base.MorePreconditions;
import com.twitter.common.net.http.handlers.TextResponseHandler;

/**
 * A simple multi-threaded HTTP server dispatcher.  Supports registering handlers for different
 * URI paths, which will be called when a request is received.
 *
 * @author William Farner
 */
public class JettyHttpServerDispatch implements HttpServerDispatch {
  private static final Logger LOG = Logger.getLogger(JettyHttpServerDispatch.class.getName());

  // Registered endpoints. Used only for display.
  private final Set<String> registeredEndpoints = Sets.newHashSet();

  private final Optional<RequestLog> requestLog;
  private Server server;
  private Context context;
  private int port;

  /**
   * Creates an HTTP server.
   */
  public JettyHttpServerDispatch() {
    this.requestLog = Optional.absent();
  }

  /**
   * Creates an HTTP server which will be configured to log requests to the provided request log.
   *
   * @param requestLog HTTP request log.
   */
  @Inject
  public JettyHttpServerDispatch(RequestLog requestLog) {
    this.requestLog = Optional.of(requestLog);
  }

  /**
   * Opens the HTTP server on the given port.
   *
   * @param port The port to listen on.
   * @return {@code true} if the server started successfully on the port, {@code false} otherwise.
   */
  public boolean listen(int port) {
    return listen(port, port);
  }

  @Override
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
    if (requestLog.isPresent()) {
      RequestLogHandler logHandler = new RequestLogHandler();
      logHandler.setRequestLog(requestLog.get());
      context.addHandler(logHandler);
    }

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

  @Override
  public synchronized boolean isStarted() {
    return (server != null) && server.isStarted();
  }

  @Override
  public synchronized int getPort() {
    Preconditions.checkState(isStarted(), "HttpServer must be started before port can be determined");
    return port;
  }

  /**
   * Opens a new Connector which is a Jetty specific way of handling the
   * lifecycle and configuration of the Jetty server. The connector will
   * open a Socket on an available port between minPort and maxPort.
   * A subclass can override this method to modify connector configurations
   * such as queue-size or header-buffer-size.
   * @param minPort the minimum port number to bind to.
   * @param maxPort the maximum port number to bind to.
   * @return
   */
  protected Connector openConnector(int minPort, int maxPort) {
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
        LOG.log(Level.WARNING, "Failed to create HTTP server on port " + port, e);
      }
    }
    return null;
  }

  @Override
  public synchronized void stop() {
    if (isStarted()) {
      try {
        server.stop();
      } catch (Exception e) {
        LOG.log(Level.SEVERE, "Error stopping HTTPServer on " + port, e);
      }
    }
  }

  @Override
  public synchronized void registerHandler(
      String path,
      HttpServlet handler,
      @Nullable Map<String, String> initParams,
      boolean silent) {

    Preconditions.checkNotNull(path);
    Preconditions.checkNotNull(handler);
    Preconditions.checkState(path.length() > 0);
    Preconditions.checkState(path.charAt(0) == '/');

    if (silent) {
      registeredEndpoints.remove(path);
    } else {
      registeredEndpoints.add(path);
    }

    ServletHolder servletHolder = new ServletHolder(handler);
    if (initParams != null) {
      servletHolder.setInitParameters(initParams);
    }
    getRootContext().addServlet(servletHolder, path.replaceFirst("/?$", "/*"));
  }

  @Override
  public synchronized void registerFilter(Class<? extends Filter> filterClass, String pathSpec) {
    MorePreconditions.checkNotBlank(pathSpec);
    Preconditions.checkNotNull(filterClass);
    getRootContext().addFilter(filterClass, pathSpec, Handler.REQUEST);
  }

  @Override
  public synchronized void registerIndexLink(String path) {
    MorePreconditions.checkNotBlank(path);
    registeredEndpoints.add(path);
  }

  @Override
  public void registerListener(ServletContextListener servletContextListener) {
    registerEventListener(servletContextListener);
  }

  @Override
  public void registerListener(ServletContextAttributeListener servletContextAttributeListener) {
    registerEventListener(servletContextAttributeListener);
  }

  @Override
  public void registerListener(ServletRequestListener servletRequestListener) {
    registerEventListener(servletRequestListener);
  }

  @Override
  public void registerListener(ServletRequestAttributeListener servletRequestAttributeListener) {
    registerEventListener(servletRequestAttributeListener);
  }

  private synchronized void registerEventListener(EventListener eventListener) {
    Preconditions.checkNotNull(eventListener);
    getRootContext().addEventListener(eventListener);
  }

  public synchronized Context getRootContext() {
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
      for (String handler : Ordering.natural().sortedCopy(registeredEndpoints)) {
        lines.add(String.format("<a href='%s'>%s</a><br />", handler, handler));
      }
      lines.add("</html>");
      return lines;
    }
  }
}
