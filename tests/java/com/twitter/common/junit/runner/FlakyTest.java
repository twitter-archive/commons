package com.twitter.common.junit.runner;

import org.junit.Assume;
import org.junit.Test;

/**
 * Contains tests that pass only after some number of retries. Used in
 * ConsoleRunnerTest.testFlakyTests(). Since this test class can also be picked
 * up on its own, independently of testFlakyTests, the flaky tests are disabled
 * by default via the TestRegistry.consoleRunnerTestRunsFlakyTests flag.
 */
public class FlakyTest {

  public static int numFlaky1Invocations = 0;
  public static int numFlaky2Invocations = 0;
  public static int numFlaky3Invocations = 0;

  @Test
  public void flakyMethodSucceedsAfter1Retry() throws Exception {
    Assume.assumeTrue(TestRegistry.consoleRunnerTestRunsFlakyTests);
    TestRegistry.registerTestCall("flaky1");
    numFlaky1Invocations++;
    if (numFlaky1Invocations < 2) {
      throw new Exception("flaky1 failed on invocation number " + numFlaky1Invocations);
    }
  }

  @Test
  public void flakyMethodSucceedsAfter2Retries() throws Exception {
    Assume.assumeTrue(TestRegistry.consoleRunnerTestRunsFlakyTests);
    TestRegistry.registerTestCall("flaky2");
    numFlaky2Invocations++;
    if (numFlaky2Invocations < 3) {
      throw new Exception("flaky2 failed on invocation number " + numFlaky2Invocations);
    }
  }

  @Test
  public void methodAlwaysFails() throws Exception {
    Assume.assumeTrue(TestRegistry.consoleRunnerTestRunsFlakyTests);
    TestRegistry.registerTestCall("flaky3");
    numFlaky3Invocations++;
    throw new Exception("flaky3 failed on invocation number " + numFlaky3Invocations);
  }

  @Test
  public void notFlakyMethod() {
    TestRegistry.registerTestCall("notflaky");
  }
}
