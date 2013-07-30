package com.foobar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExampleTest {
  @Test
  public void testLoadResources() throws Exception {
    assertEquals("Hello, World!\n", Example.loadResource());
  }
}
