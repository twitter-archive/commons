package com.twitter.common.application;

import com.twitter.common.base.ExceptionalCommand;

/**
 * Local interface to define requirements for a closure that is responsible for launching
 * a service at startup.
 *
 * @param <E> Exception type thrown during service launch.
 */
public interface ServiceLauncher<E extends Exception> extends ExceptionalCommand<E> {

  /**
   * Gets the service port name.
   *
   * @return Port name.
   */
  String getPortName();

  /**
   * Checks whether this is the primary service in the application.  There may be at most one
   * primary service in an application.
   *
   * @return {@code true} if this launcher is responsible for reporting the primary application
   *     service, {@code false} otherwise.
   */
  boolean isPrimaryService();
}
