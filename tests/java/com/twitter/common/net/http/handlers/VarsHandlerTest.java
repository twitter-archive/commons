package com.twitter.common.net.http.handlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.stats.Stat;

import static org.junit.Assert.assertEquals;

/**
 * @author William Farner
 */
public class VarsHandlerTest extends StatSupplierTestBase {

  private VarsHandler vars;
  private HttpServletRequest request;

  @Before
  public void setUp() {
    statSupplier = createMock(new Clazz<Supplier<Iterable<Stat<?>>>>() {});
    request = createMock(HttpServletRequest.class);
    vars = new VarsHandler(statSupplier);
  }

  @Test
  public void testGetEmpty() {
    expectVarScrape(ImmutableMap.<String, Object>of());

    control.replay();

    checkOutput(Collections.<String>emptyList());
  }

  @Test
  public void testGet() {
    expectVarScrape(ImmutableMap.<String, Object>of(
        "float", 4.16126,
        "int", 5,
        "str", "foobar"
    ));

    control.replay();

    // expect the output to be in the same order
    checkOutput(Arrays.asList(
        "float 4.16126",
        "int 5",
        "str foobar"));
  }

  private void checkOutput(List<String> expectedLines) {
    assertEquals(expectedLines,
        ImmutableList.copyOf(vars.getLines(request)));
  }
}
