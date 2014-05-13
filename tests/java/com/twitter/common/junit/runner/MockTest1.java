package com.twitter.common.junit.runner;

import org.junit.Test;

public class MockTest1 {

  @Test
  public void testMethod11() {
    TestRegistry.registerTestCall("test11");
  }

  @Test
  public void testMethod12() {
    TestRegistry.registerTestCall("test12");
  }

  @Test
  public void testMethod13() {
    TestRegistry.registerTestCall("test13");
  }
}
