package com.twitter.common.application.http;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URL;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

import com.google.common.io.Resources;
import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.multibindings.Multibinder;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Utility class for registering HTTP servlets and assets.
 */
public final class Registration {

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
   * A binding annotation applied to the set of additional index page links bound via
   * {@link #Registration#registerEndpoint()}
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface IndexLink { }

  /**
   * Gets the multibinder used to bind links on the root servlet.
   * The resulting {@link java.util.Set} is bound with the {@link IndexLink} annotation.
   *
   * @param binder a guice binder to associate the multibinder with.
   * @return The multibinder to bind index links against.
   */
  public static Multibinder<String> getEndpointBinder(Binder binder) {
    return Multibinder.newSetBinder(binder, String.class, IndexLink.class);
  }

  /**
   * Registers a link to display on the root servlet.
   *
   * @param binder a guice binder to register the link with.
   * @param endpoint Endpoint URI to include.
   */
  public static void registerEndpoint(Binder binder, String endpoint) {
    getEndpointBinder(binder).addBinding().toInstance(endpoint);
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
  public static void registerHttpAsset(
      Binder binder,
      String servedPath,
      Class<?> contextClass,
      String assetRelativePath,
      String assetType,
      boolean silent) {

    registerHttpAsset(binder, servedPath, Resources.getResource(contextClass, assetRelativePath),
        assetType, silent);
  }

  /**
   * Gets the multibinder used to bind HTTP filters.
   *
   * @param binder a guice binder to associate the multibinder with.
   * @return The multibinder to bind HTTP filter configurations against.
   */
  public static Multibinder<HttpFilterConfig> getFilterBinder(Binder binder) {
    return Multibinder.newSetBinder(binder, HttpFilterConfig.class);
  }

  /**
   * Registers an HTTP servlet filter.
   *
   * @param binder a guice binder to register the filter with.
   * @param filterClass Filter class to register.
   * @param pathSpec Path spec that the filter should be activated on.
   */
  public static void registerServletFilter(
      Binder binder,
      Class<? extends Filter> filterClass,
      String pathSpec) {

    getFilterBinder(binder).addBinding().toInstance(new HttpFilterConfig(filterClass, pathSpec));
  }
}
