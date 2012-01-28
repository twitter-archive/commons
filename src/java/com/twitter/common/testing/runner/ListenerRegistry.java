package com.twitter.common.testing.runner;

import org.junit.runner.notification.RunListener;

/**
 * Registers {@link RunListener RunListeners} for callbacks during a a test run session.
 */
interface ListenerRegistry {

  /**
   * Registers the {@code listener} for callbacks.
   *
   * @param listener The listener to register.
   */
  void addListener(RunListener listener);
}
