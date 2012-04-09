package com.twitter.common.testing.runner;

import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * A listener that fails a test run on its first failure.
 */
abstract class FailFastListener extends ForwardingListener {
  private final Result result = new Result();

  FailFastListener() {
    addListener(result.createListener());
  }

  @Override
  public void testFailure(Failure failure) throws Exception {
    // Allow any listeners to handle the failure in the normal way first.
    super.testFailure(failure);

    // Simulate the junit test run lifecycle end.
    testRunFinished(result);

    // Allow the subclass to actually stop the test run.
    failFast(result);
  }

  /**
   * Called on the first test failure.  Its expected that subclasses will halt the test run in some
   * way.
   *
   * @param failureResult The test result for the failing suite up to and including the first
   *     failure
   */
  protected abstract void failFast(Result failureResult);
}
