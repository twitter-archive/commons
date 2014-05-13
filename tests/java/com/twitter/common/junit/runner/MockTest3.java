package com.twitter.common.junit.runner;

import org.junit.Test;

public class MockTest3 {

  @Test
  public void testMethod31() {
    TestRegistry.registerTestCall("test31");
  }

  @Test
  public void testMethod32() {
    TestRegistry.registerTestCall("test32");
  }
}
