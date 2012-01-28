package com.twitter.common.net.http.handlers;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author William Farner
 */
public class VarsJsonHandlerTest extends StatSupplierTestBase {

  private VarsJsonHandler varsJson;

  @Before
  public void setUp() {
    varsJson = new VarsJsonHandler(statSupplier);
  }

  @Test
  public void testGetEmpty() {
    expectVarScrape(ImmutableMap.<String, Object>of());

    control.replay();

    assertEquals("{}", varsJson.getBody(false));
  }

  @Test
  public void testGet() {
    expectVarScrape(ImmutableMap.<String, Object>of(
        "str", "foobar",
        "int", 5,
        "float", 4.16126
    ));

    control.replay();

    assertEquals("{\"str\":\"foobar\",\"int\":5,\"float\":4.16126}", varsJson.getBody(false));
  }

  @Test
  public void testGetPretty() {
    expectVarScrape(ImmutableMap.<String, Object>of(
        "str", "foobar",
        "int", 5,
        "float", 4.16126
    ));

    control.replay();

    assertEquals("{\n" +
        "  \"str\": \"foobar\",\n" +
        "  \"int\": 5,\n" +
        "  \"float\": 4.16126\n" +
        "}", varsJson.getBody(true));
  }
}
