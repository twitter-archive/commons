package com.twitter.common.testing.runner;

import java.io.PrintStream;

import org.junit.internal.TextListener;
import org.junit.runner.notification.Failure;

/**
 * A run listener that logs test events with single characters.
 */
class ConsoleListener extends TextListener {
  private final PrintStream out;

  ConsoleListener(PrintStream out) {
    super(out);
    this.out = out;
  }

  @Override
  public void testFailure(Failure failure) {
    out.append(Util.isAssertionFailure(failure) ? 'F' : 'E');
  }
}
