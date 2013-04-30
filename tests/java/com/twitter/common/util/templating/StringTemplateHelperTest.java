package com.twitter.common.util.templating;

import java.io.StringWriter;
import java.util.Arrays;

import org.antlr.stringtemplate.StringTemplate;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.Closure;
import com.twitter.common.util.templating.StringTemplateHelper.TemplateException;

import static org.junit.Assert.assertEquals;

public class StringTemplateHelperTest {

  private StringTemplateHelper templateHelper;

  @Before
  public void setUp() {
    templateHelper = new StringTemplateHelper(getClass(), "template", false);
  }

  private static class Item {
    final String name;
    final int price;

    private Item(String name, int price) {
      this.name = name;
      this.price = price;
    }

    public String getName() {
      return name;
    }

    public int getPrice() {
      return price;
    }
  }

  @Test
  public void testFillTemplate() throws Exception {
    StringWriter output = new StringWriter();
    templateHelper.writeTemplate(output, new Closure<StringTemplate>() {
      @Override public void execute(StringTemplate template) {
        template.setAttribute("header", "Prices");
        template.setAttribute("items", Arrays.asList(
            new Item("banana", 50),
            new Item("car", 2),
            new Item("jupiter", 200)
        ));
        template.setAttribute("footer", "The End");
      }
    });
    String expected = "Prices\n"
        + "\n  The banana costs $50."
        + "\n  The car costs $2."
        + "\n  The jupiter costs $200.\n"
        + "\n\nThe End";
    assertEquals(expected, output.toString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMissingTemplate() throws Exception {
    new StringTemplateHelper(getClass(), "missing_template", false);
  }

  private static class CustomException extends RuntimeException {
  }

  @Test(expected = CustomException.class)
  public void testClosureError() throws Exception {
    templateHelper.writeTemplate(new StringWriter(), new Closure<StringTemplate>() {
      @Override public void execute(StringTemplate template) {
        throw new CustomException();
      }
    });
  }
}
