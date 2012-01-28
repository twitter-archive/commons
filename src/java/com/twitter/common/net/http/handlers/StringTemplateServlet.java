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
import com.google.inject.BindingAnnotation;
import com.twitter.common.base.ExceptionalClosure;
import com.twitter.common.base.MorePreconditions;
import org.antlr.stringtemplate.AutoIndentWriter;
import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;

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
 *
 * @author John Sirois
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

  private final StringTemplateGroup group;
  private final String templatePath;

  /**
   * Creates a new StringTemplateServlet that expects to find its template located in the same
   * package on the classpath at '{@code templateName}.st'.
   *
   * @param templateName The name of the string template to use.
   * @param cacheTemplates {@code true} to re-use loaded templates, {@code false} to reload the
   *     template for each request.
   */
  protected StringTemplateServlet(String templateName, boolean cacheTemplates) {
    MorePreconditions.checkNotBlank(templateName);
    String templatePath = getClass().getPackage().getName().replace('.', '/') + "/" + templateName;
    StringTemplateGroup group = new StringTemplateGroup(templateName);
    Preconditions.checkNotNull(group.getInstanceOf(templatePath),
        "Failed to load template at: %s", templatePath);

    this.group = group;
    if (!cacheTemplates) {
      group.setRefreshInterval(0);
    }
    this.templatePath = templatePath;
  }

  protected final void writeTemplate(HttpServletResponse response,
        ExceptionalClosure<StringTemplate, ?> parameterSetter) throws IOException {
    writeTemplate(response, CONTENT_TYPE_TEXT_HTML, HttpServletResponse.SC_OK, parameterSetter);
  }

  protected final void writeTemplate(HttpServletResponse response, String contentType, int status,
      ExceptionalClosure<StringTemplate, ?> parameterSetter) throws IOException {
    Preconditions.checkNotNull(response);
    MorePreconditions.checkNotBlank(contentType);
    Preconditions.checkArgument(status > 0);
    Preconditions.checkNotNull(parameterSetter);

    StringTemplate stringTemplate = group.getInstanceOf(templatePath);
    try {
      parameterSetter.execute(stringTemplate);
      response.setStatus(status);
      response.setContentType(contentType);
      stringTemplate.write(new AutoIndentWriter(response.getWriter()));
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Unknown exception.", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
