// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.application.modules;

import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import com.twitter.common.application.ActionRegistry;
import com.twitter.common.application.ShutdownStage;
import com.twitter.common.application.StartupStage;
import com.twitter.common.application.http.DefaultQuitHandler;
import com.twitter.common.application.http.GraphViewer;
import com.twitter.common.application.http.HttpAssetConfig;
import com.twitter.common.application.http.HttpServletConfig;
import com.twitter.common.application.http.Registration;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.Range;
import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.base.Supplier;
import com.twitter.common.net.http.HttpServerDispatch;
import com.twitter.common.net.http.handlers.AbortHandler;
import com.twitter.common.net.http.handlers.ContentionPrinter;
import com.twitter.common.net.http.handlers.HealthHandler;
import com.twitter.common.net.http.handlers.LogConfig;
import com.twitter.common.net.http.handlers.LogPrinter;
import com.twitter.common.net.http.handlers.QuitHandler;
import com.twitter.common.net.http.handlers.StringTemplateServlet.CacheTemplates;
import com.twitter.common.net.http.handlers.ThreadStackPrinter;
import com.twitter.common.net.http.handlers.ThriftServlet;
import com.twitter.common.net.http.handlers.TimeSeriesDataSource;
import com.twitter.common.net.http.handlers.VarsHandler;

/**
 * Binding module for injections related to the HTTP server and the default set of servlets.
 *
 * This module uses a single command line argument 'http_port'.  If unset, the HTTP server will
 * be started on an ephemeral port.
 *
 * The default HTTP server includes several generic servlets that are useful for debugging.
 *
 * This class also offers several convenience methods for other modules to register HTTP servlets
 * which will be included in the HTTP server configuration.
 *
 * Bindings provided by this module:
 * <ul>
 *   <li>{@code @CacheTemplates boolean} - True if parsed stringtemplates for servlets are cached.
 * </ul>
 *
 * Bindings that may be overridden with an override module:
 * <ul>
 *   <li>Abort handler: called when an HTTP GET request is issued to the /abortabortabort HTTP
 *       servlet.  May be overridden by binding to:
 *       {@code bind(Runnable.class).annotatedWith(Names.named(AbortHandler.ABORT_HANDLER_KEY))}.
 *   <li>Quit handler: called when an HTTP GET request is issued to the /quitquitquit HTTP
 *       servlet.  May be overridden by binding to:
 *       {@code bind(Runnable.class).annotatedWith(Names.named(QuitHandler.QUIT_HANDLER_KEY))}.
 *   <li>Health checker: called to determine whether the application is healthy to serve an
 *       HTTP GET request to /health.  May be overridden by binding to:
 *       {@code bind(new TypeLiteral<ExceptionalSupplier<Boolean, ?>>() {}).annotatedWith(Names.named(HealthHandler.HEALTH_CHECKER_KEY))}.
 *   <li>
 * </ul>
 *
 * @author William Farner
 */
public class HttpModule extends AbstractModule {

  private static final Logger LOG = Logger.getLogger(HttpModule.class.getName());

  // TODO(William Farner): Consider making this configurable if needed.
  private static final boolean CACHE_TEMPLATES = true;

  private static final Runnable DEFAULT_ABORT_HANDLER = new Runnable() {
      @Override public void run() {
        LOG.info("ABORTING PROCESS IMMEDIATELY!");
        System.exit(0);
      }
    };
  private static final Supplier<Boolean> DEFAULT_HEALTH_CHECKER = new Supplier<Boolean>() {
      @Override public Boolean get() {
        return Boolean.TRUE;
      }
    };

  @Range(lower=0, upper=65535)
  @CmdLine(name = "http_port",
           help = "The port to start an HTTP server on.  Default value will choose a random port.")
  protected static final Arg<Integer> HTTP_PORT = Arg.create(0);

  @Override
  protected void configure() {
    requireBinding(Injector.class);
    requireBinding(Key.get(ActionRegistry.class, StartupStage.class));
    requireBinding(Key.get(ActionRegistry.class, ShutdownStage.class));

    // Bind the default abort, quit, and health check handlers.
    bind(Key.get(Runnable.class, Names.named(AbortHandler.ABORT_HANDLER_KEY)))
        .toInstance(DEFAULT_ABORT_HANDLER);
    bind(Runnable.class).annotatedWith(Names.named(QuitHandler.QUIT_HANDLER_KEY))
        .to(DefaultQuitHandler.class);
    bind(DefaultQuitHandler.class).in(Singleton.class);
    bind(new TypeLiteral<ExceptionalSupplier<Boolean, ?>>() {})
        .annotatedWith(Names.named(HealthHandler.HEALTH_CHECKER_KEY))
        .toInstance(DEFAULT_HEALTH_CHECKER);

    // Allow template reloading in interactive mode for easy debugging of string templates.
    bindConstant().annotatedWith(CacheTemplates.class).to(CACHE_TEMPLATES);

    bind(HttpServerDispatch.class).in(Singleton.class);
    Registration.registerServlet(binder(), "/abortabortabort", AbortHandler.class, true);
    Registration.registerServlet(binder(), "/contention", ContentionPrinter.class, false);
    Registration.registerServlet(binder(), "/graphdata", TimeSeriesDataSource.class, true);
    Registration.registerServlet(binder(), "/health", HealthHandler.class, true);
    Registration.registerServlet(binder(), "/logconfig", LogConfig.class, false);
    Registration.registerServlet(binder(), "/logs", LogPrinter.class, false);
    Registration.registerServlet(binder(), "/quitquitquit", QuitHandler.class, true);
    Registration.registerServlet(binder(), "/threads", ThreadStackPrinter.class, false);
    Registration.registerServlet(binder(), "/vars", VarsHandler.class, false);

    GraphViewer.registerResources(binder());

    requestStaticInjection(Init.class);
  }

  // TODO(William Farner): Use a global Multibinder for startup and shutdown actions to obviate the need
  //    for these awkward init classes.
  public static class Init {
    private static final Logger LOG = Logger.getLogger(Init.class.getName());

    @Inject
    private static void startHttpServer(
        @StartupStage ActionRegistry startupRegistry,
        @ShutdownStage ActionRegistry shutdownRegistry,
        final HttpServerDispatch httpServerDispatch,
        final Set<HttpServletConfig> httpServlets,
        final Set<HttpAssetConfig> httpAssets,
        final Injector injector) {

      startupRegistry.addAction(new Command() {
        @Override public void execute() {
          if (!httpServerDispatch.listen(HTTP_PORT.get())) {
            LOG.severe("Failed to start HTTP server, all servlets disabled.");
            return;
          }

          for (HttpServletConfig config : httpServlets) {
            HttpServlet handler = injector.getInstance(config.handlerClass);
            httpServerDispatch.registerHandler(config.path, handler, config.params, config.silent);
          }

          for (HttpAssetConfig config : httpAssets) {
            httpServerDispatch.registerHandler(config.path, config.handler, null, config.silent);
          }
        }
      });

      shutdownRegistry.addAction(new Command() {
        @Override public void execute() {
          LOG.info("Shutting down embedded http server");
          httpServerDispatch.stop();
        }
      });
    }
  }
}
