package com.twitter.common.junit.runner;

import java.io.PrintStream;

import org.junit.internal.requests.ClassRequest;
import org.junit.runner.Runner;

import com.twitter.common.junit.runner.withretry.AllDefaultPossibilitiesBuilderWithRetry;

/**
 * A ClassRequest that exposes the wrapped class. Also used to support retrying
 * flaky tests via AllDefaultPossibilitiesBuilderWithRetry, that in turn gives us
 * access to other code inside JUnit4, that cannot be customized in a simpler way.
 */
public class AnnotatedClassRequest extends ClassRequest {

  private final Class<?> testClass;
  private final int numRetries;
  private final PrintStream err;

  /**
   * Constructs an instance for the given test class, number of retries for failing tests
   * (0 means no retries) and a stream to print the information about flaky tests (those
   * that first fail but then pass after retrying).
   */
  public AnnotatedClassRequest(Class<?> testClass, int numRetries, PrintStream err) {
    super(testClass);
    this.testClass = testClass;
    this.numRetries = numRetries;
    this.err = err;
  }

  public Class<?> getClazz() {
    return testClass;
  }

  @Override
  public Runner getRunner() {
    return new
        AllDefaultPossibilitiesBuilderWithRetry(numRetries, err).safeRunnerForClass(testClass);
  }
}
