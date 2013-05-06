package com.twitter.common.net.http.handlers;

import com.google.common.base.Preconditions;
import com.google.inject.BindingAnnotation;

import com.twitter.common.base.Closure;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.util.templating.StringTemplateHelper;
import com.twitter.common.util.templating.StringTemplateHelper.TemplateException;

import org.antlr.stringtemplate.StringTemplate;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A base class for servlets that render using the string template templating system.  Subclasses
 * can call one of the {@link #writeTemplate} methods to render their content with the associated
 * template.
 */
public abstract class StringTemplateServlet extends HttpServlet {
  private static final String CONTENT_TYPE_TEXT_HTML = "text/html";

  /**
   * A {@literal @BindingAnnotation} that allows configuration of whether or not
   * StringTemplateServlets should cache their templates.
   */
  @BindingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.PARAMETER, ElementType.METHOD})
  public @interface CacheTemplates {}

  private static final Logger LOG = Logger.getLogger(StringTemplateServlet.class.getName());

  private final StringTemplateHelper templateHelper;

  /**
   * Creates a new StringTemplateServlet that expects to find its template located in the same
   * package on the classpath at '{@code templateName}.st'.
   *
   * @param templateName The name of the string template to use.
   * @param cacheTemplates {@code true} to re-use loaded templates, {@code false} to reload the
   *     template for each request.
   */
  protected StringTemplateServlet(String templateName, boolean cacheTemplates) {
    templateHelper = new StringTemplateHelper(getClass(), templateName, cacheTemplates);
  }

  protected final void writeTemplate(
      HttpServletResponse response,
      Closure<StringTemplate> parameterSetter) throws IOException {

    writeTemplate(response, CONTENT_TYPE_TEXT_HTML, HttpServletResponse.SC_OK, parameterSetter);
  }

  protected final void writeTemplate(
      HttpServletResponse response,
      String contentType,
      int status,
      Closure<StringTemplate> parameterSetter) throws IOException {

    Preconditions.checkNotNull(response);
    MorePreconditions.checkNotBlank(contentType);
    Preconditions.checkArgument(status > 0);
    Preconditions.checkNotNull(parameterSetter);

    try {
      templateHelper.writeTemplate(response.getWriter(), parameterSetter);
      response.setStatus(status);
      response.setContentType(contentType);
    } catch (TemplateException e) {
      LOG.log(Level.SEVERE, "Unknown exception.", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
