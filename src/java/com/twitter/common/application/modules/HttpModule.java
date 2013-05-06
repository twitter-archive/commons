package com.twitter.common.application.modules;

import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;

import org.mortbay.jetty.RequestLog;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.application.http.DefaultQuitHandler;
import com.twitter.common.application.http.GraphViewer;
import com.twitter.common.application.http.HttpAssetConfig;
import com.twitter.common.application.http.HttpFilterConfig;
import com.twitter.common.application.http.HttpServletConfig;
import com.twitter.common.application.http.Registration;
import com.twitter.common.application.http.Registration.IndexLink;
import com.twitter.common.application.modules.LifecycleModule.ServiceRunner;
import com.twitter.common.application.modules.LocalServiceRegistry.LocalService;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotEmpty;
import com.twitter.common.args.constraints.Range;
import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.base.Supplier;
import com.twitter.common.net.http.HttpServerDispatch;
import com.twitter.common.net.http.JettyHttpServerDispatch;
import com.twitter.common.net.http.RequestLogger;
import com.twitter.common.net.http.handlers.AbortHandler;
import com.twitter.common.net.http.handlers.ContentionPrinter;
import com.twitter.common.net.http.handlers.HealthHandler;
import com.twitter.common.net.http.handlers.LogConfig;
import com.twitter.common.net.http.handlers.LogPrinter;
import com.twitter.common.net.http.handlers.QuitHandler;
import com.twitter.common.net.http.handlers.StringTemplateServlet.CacheTemplates;
import com.twitter.common.net.http.handlers.ThreadStackPrinter;
import com.twitter.common.net.http.handlers.TimeSeriesDataSource;
import com.twitter.common.net.http.handlers.VarsHandler;
import com.twitter.common.net.http.handlers.VarsJsonHandler;
import com.twitter.common.net.http.handlers.pprof.ContentionProfileHandler;
import com.twitter.common.net.http.handlers.pprof.CpuProfileHandler;
import com.twitter.common.net.http.handlers.pprof.HeapProfileHandler;

import static com.google.common.base.Preconditions.checkNotNull;

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
 */
public class HttpModule extends AbstractModule {

  @Range(lower = 0, upper = 65535)
  @CmdLine(name = "http_port",
           help = "The port to start an HTTP server on.  Default value will choose a random port.")
  protected static final Arg<Integer> HTTP_PORT = Arg.create(0);

  @CmdLine(name = "http_primary_service", help = "True if HTTP is the primary service.")
  protected static final Arg<Boolean> HTTP_PRIMARY_SERVICE = Arg.create(false);

  @NotEmpty
  @CmdLine(name = "http_announce_port_names",
      help = "Names to identify the HTTP port with when advertising the service.")
  protected static final Arg<Set<String>> ANNOUNCE_NAMES =
      Arg.<Set<String>>create(ImmutableSet.of("http"));

  private static final Logger LOG = Logger.getLogger(HttpModule.class.getName());

  // TODO(William Farner): Consider making this configurable if needed.
  private static final boolean CACHE_TEMPLATES = true;

  private static class DefaultAbortHandler implements Runnable {
    @Override public void run() {
      LOG.info("ABORTING PROCESS IMMEDIATELY!");
      System.exit(0);
    }
  }

  private static class DefaultHealthChecker implements Supplier<Boolean> {
    @Override public Boolean get() {
      return Boolean.TRUE;
    }
  }

  private final Key<? extends Runnable> abortHandler;
  private final Key<? extends Runnable> quitHandlerKey;
  private final Key<? extends ExceptionalSupplier<Boolean, ?>> healthCheckerKey;

  public HttpModule() {
    this(builder());
  }

  private HttpModule(Builder builder) {
    this.abortHandler = checkNotNull(builder.abortHandlerKey);
    this.quitHandlerKey = checkNotNull(builder.quitHandlerKey);
    this.healthCheckerKey = checkNotNull(builder.healthCheckerKey);
  }

  /**
   * Creates a builder to override default bindings.
   *
   * @return A new builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder to customize bindings.
   */
  public static class Builder {
    private Key<? extends Runnable> abortHandlerKey = Key.get(DefaultAbortHandler.class);
    private Key<? extends Runnable> quitHandlerKey = Key.get(DefaultQuitHandler.class);
    private Key<? extends ExceptionalSupplier<Boolean, ?>> healthCheckerKey =
        Key.get(DefaultHealthChecker.class);

    /**
     * Specifies a custom abort handler to be invoked when an HTTP abort signal is received.
     *
     * @param key Abort callback handler binding key.
     * @return A reference to this builder.
     */
    public Builder abortHandler(Key<? extends Runnable> key) {
      this.abortHandlerKey = key;
      return this;
    }

    /**
     * Specifies a custom quit handler to be invoked when an HTTP quit signal is received.
     *
     * @param key Quit callback handler binding key.
     * @return A reference to this builder.
     */
    public Builder quitHandler(Key<? extends Runnable> key) {
      this.quitHandlerKey = key;
      return this;
    }

    /**
     * Specifies a custom health checker to control responses to HTTP health checks.
     *
     * @param key Health check callback binding key.
     * @return A reference to this builder.
     */
    public Builder healthChecker(Key<? extends ExceptionalSupplier<Boolean, ?>> key) {
      this.healthCheckerKey = key;
      return this;
    }

    /**
     * Constructs an http module.
     *
     * @return An http module constructed from this builder.
     */
    public HttpModule build() {
      return new HttpModule(this);
    }
  }

  @Override
  protected void configure() {
    requireBinding(Injector.class);
    requireBinding(ShutdownRegistry.class);

    bind(Runnable.class)
        .annotatedWith(Names.named(AbortHandler.ABORT_HANDLER_KEY))
        .to(abortHandler);
    bind(abortHandler).in(Singleton.class);
    bind(Runnable.class).annotatedWith(Names.named(QuitHandler.QUIT_HANDLER_KEY))
        .to(quitHandlerKey);
    bind(quitHandlerKey).in(Singleton.class);
    bind(new TypeLiteral<ExceptionalSupplier<Boolean, ?>>() { })
        .annotatedWith(Names.named(HealthHandler.HEALTH_CHECKER_KEY))
        .to(healthCheckerKey);
    bind(healthCheckerKey).in(Singleton.class);

    // Allow template reloading in interactive mode for easy debugging of string templates.
    bindConstant().annotatedWith(CacheTemplates.class).to(CACHE_TEMPLATES);

    bind(HttpServerDispatch.class).to(JettyHttpServerDispatch.class)
        .in(Singleton.class);
    bind(RequestLog.class).to(RequestLogger.class);
    Registration.registerServlet(binder(), "/abortabortabort", AbortHandler.class, true);
    Registration.registerServlet(binder(), "/contention", ContentionPrinter.class, false);
    Registration.registerServlet(binder(), "/graphdata", TimeSeriesDataSource.class, true);
    Registration.registerServlet(binder(), "/health", HealthHandler.class, true);
    Registration.registerServlet(binder(), "/healthz", HealthHandler.class, true);
    Registration.registerServlet(binder(), "/logconfig", LogConfig.class, false);
    Registration.registerServlet(binder(), "/logs", LogPrinter.class, false);
    Registration.registerServlet(binder(), "/pprof/heap", HeapProfileHandler.class, false);
    Registration.registerServlet(binder(), "/pprof/profile", CpuProfileHandler.class, false);
    Registration.registerServlet(
        binder(), "/pprof/contention", ContentionProfileHandler.class, false);
    Registration.registerServlet(binder(), "/quitquitquit", QuitHandler.class, true);
    Registration.registerServlet(binder(), "/threads", ThreadStackPrinter.class, false);
    Registration.registerServlet(binder(), "/vars", VarsHandler.class, false);
    Registration.registerServlet(binder(), "/vars.json", VarsJsonHandler.class, false);

    GraphViewer.registerResources(binder());

    LifecycleModule.bindServiceRunner(binder(), HttpServerLauncher.class);

    // Ensure at least an empty filter set is bound.
    Registration.getFilterBinder(binder());

    // Ensure at least an empty set of additional links is bound.
    Registration.getEndpointBinder(binder());
  }

  public static final class HttpServerLauncher implements ServiceRunner {
    private final HttpServerDispatch httpServer;
    private final Set<HttpServletConfig> httpServlets;
    private final Set<HttpAssetConfig> httpAssets;
    private final Set<HttpFilterConfig> httpFilters;
    private final Set<String> additionalIndexLinks;
    private final Injector injector;

    @Inject HttpServerLauncher(
        HttpServerDispatch httpServer,
        Set<HttpServletConfig> httpServlets,
        Set<HttpAssetConfig> httpAssets,
        Set<HttpFilterConfig> httpFilters,
        @IndexLink Set<String> additionalIndexLinks,
        Injector injector) {

      this.httpServer = checkNotNull(httpServer);
      this.httpServlets = checkNotNull(httpServlets);
      this.httpAssets = checkNotNull(httpAssets);
      this.httpFilters = checkNotNull(httpFilters);
      this.additionalIndexLinks = checkNotNull(additionalIndexLinks);
      this.injector = checkNotNull(injector);
    }

    @Override public LocalService launch() {
      if (!httpServer.listen(HTTP_PORT.get())) {
        throw new IllegalStateException("Failed to start HTTP server, all servlets disabled.");
      }

      httpServer.registerListener(new GuiceServletContextListener() {
        @Override protected Injector getInjector() {
          return injector;
        }
      });
      httpServer.registerFilter(GuiceFilter.class, "/*");

      for (HttpServletConfig config : httpServlets) {
        HttpServlet handler = injector.getInstance(config.handlerClass);
        httpServer.registerHandler(config.path, handler, config.params, config.silent);
      }

      for (HttpAssetConfig config : httpAssets) {
        httpServer.registerHandler(config.path, config.handler, null, config.silent);
      }

      for (HttpFilterConfig filter : httpFilters) {
        httpServer.registerFilter(filter.filterClass, filter.pathSpec);
      }

      for (String indexLink : additionalIndexLinks) {
        httpServer.registerIndexLink(indexLink);
      }

      Command shutdown = new Command() {
        @Override public void execute() {
          LOG.info("Shutting down embedded http server");
          httpServer.stop();
        }
      };

      return HTTP_PRIMARY_SERVICE.get()
          ? LocalService.primaryService(httpServer.getPort(), shutdown)
          : LocalService.auxiliaryService(ANNOUNCE_NAMES.get(), httpServer.getPort(), shutdown);
    }
  }
}
