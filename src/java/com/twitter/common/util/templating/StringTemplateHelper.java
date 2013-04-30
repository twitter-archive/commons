package com.twitter.common.util.templating;

import java.io.IOException;
import java.io.Writer;

import com.google.common.base.Preconditions;

import org.antlr.stringtemplate.AutoIndentWriter;
import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;

import com.twitter.common.base.Closure;
import com.twitter.common.base.MorePreconditions;

/**
 * A class to simplify the operations required to load a stringtemplate template file from the
 * classpath and populate it.
 */
public class StringTemplateHelper {

  private final StringTemplateGroup group;
  private final String templatePath;

  /**
   * Creates a new template helper.
   *
   * @param templateContextClass Classpath context for the location of the template file.
   * @param templateName Template file name (excluding .st suffix) relative to
   *     {@code templateContextClass}.
   * @param cacheTemplates Whether the template should be cached.
   */
  public StringTemplateHelper(
      Class<?> templateContextClass,
      String templateName,
      boolean cacheTemplates) {

    MorePreconditions.checkNotBlank(templateName);
    String templatePath =
        templateContextClass.getPackage().getName().replace('.', '/') + "/" + templateName;
    StringTemplateGroup group = new StringTemplateGroup(templateName);
    Preconditions.checkNotNull(group.getInstanceOf(templatePath),
        "Failed to load template at: %s", templatePath);

    this.group = group;
    if (!cacheTemplates) {
      group.setRefreshInterval(0);
    }
    this.templatePath = templatePath;
  }

  /**
   * Thrown when an exception is encountered while populating a template.
   */
  public static class TemplateException extends Exception {
    public TemplateException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  /**
   * Writes the populated template to an output writer by providing a closure with access to
   * the unpopulated template object.
   *
   * @param out Template output writer.
   * @param parameterSetter Closure to populate the template.
   * @throws TemplateException If an exception was encountered while populating the template.
   */
  public void writeTemplate(
      Writer out,
      Closure<StringTemplate> parameterSetter) throws TemplateException {

    Preconditions.checkNotNull(out);
    Preconditions.checkNotNull(parameterSetter);

    StringTemplate stringTemplate = group.getInstanceOf(templatePath);
    try {
      parameterSetter.execute(stringTemplate);
      stringTemplate.write(new AutoIndentWriter(out));
    } catch (IOException e) {
      throw new TemplateException("Failed to write template: " + e, e);
    }
  }
}
