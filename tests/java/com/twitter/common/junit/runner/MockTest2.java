package com.twitter.common.junit.runner;

import org.junit.Test;

public class MockTest2 {

  @Test
  public void testMethod21() {
    TestRegistry.registerTestCall("test21");
  }

  @Test
  public void testMethod22() {
    TestRegistry.registerTestCall("test22");
  }
}
