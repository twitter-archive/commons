package com.twitter.common.junit.runner.withretry;

import java.io.PrintStream;
import java.lang.reflect.Method;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

/**
 * A subclass of BlockJUnit4ClassRunner that supports retrying failing tests, up to the
 * specified number of attempts. This is useful if some tests are known or suspected
 * to be flaky.
 */
public class BlockJUnit4ClassRunnerWithRetry extends BlockJUnit4ClassRunner {

  private final int numRetries;
  private final PrintStream err;

  public BlockJUnit4ClassRunnerWithRetry(Class<?> klass, int numRetries, PrintStream err)
      throws InitializationError {
    super(klass);
    this.numRetries = numRetries;
    this.err = err;
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    Statement invokeMethod = super.methodInvoker(method, test);
    return new InvokeWithRetry(invokeMethod, method);
  }

  private class InvokeWithRetry extends Statement {

    private final Statement fNext;
    private final FrameworkMethod method;

    public InvokeWithRetry(Statement next, FrameworkMethod method) {
      fNext = next;
      this.method = method;
    }

    @Override
    public void evaluate() throws Throwable {
      Throwable error = null;
      for (int i = 0; i <= numRetries; i++) {
        try {
          fNext.evaluate();
          // The test succeeded. However, if it has been retried, it's flaky.
          if (i > 0) {
            Method m = method.getMethod();
            String testName = m.getName() + '(' + m.getDeclaringClass().getName() + ')';
            err.println("Test " + testName + " is FLAKY; passed after " + (i + 1) + " attempts");
          }
          return;
        } catch (Throwable t) {
          // Test failed - save the very first thrown exception. However, if we caught an
          // Error other than AssertionError, exit immediately. It probably doesn't make
          // sense to retry a test after an OOM or LinkageError.
          if (t instanceof Exception || t instanceof AssertionError) {
            if (error == null) {
              error = t;
            }
          } else {
            throw t;
          }
        }
      }
      throw error;
    }
  }

}
