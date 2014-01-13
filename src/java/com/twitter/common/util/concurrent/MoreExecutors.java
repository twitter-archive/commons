package com.twitter.common.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Utility class that provides factory functions to decorate
 * {@link java.util.concurrent.ExecutorService}s.
 */
public final class MoreExecutors {
  private MoreExecutors() {
    // utility
  }

  /**
   * Returns a {@link ExecutorService} that passes uncaught exceptions to
   * {@link java.lang.Thread.UncaughtExceptionHandler}.
   * <p>
   * This may be useful because {@link java.util.concurrent.ThreadPoolExecutor} and
   * {@link java.util.concurrent.ScheduledThreadPoolExecutor} provide no built-in propagation of
   * unchecked exceptions thrown from submitted work. Some users are surprised to find that
   * even the default uncaught exception handler is not invoked.
   *
   * @param executorService delegate
   * @param uncaughtExceptionHandler exception handler that will receive exceptions generated
   *                                 from executor tasks.
   * @return a decorated executor service
   */
  public static ExecutorService exceptionHandlingExecutor(
      ExecutorService executorService,
      Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {

    Preconditions.checkNotNull(uncaughtExceptionHandler);
    return new ExceptionHandlingExecutorService(
        executorService, Suppliers.ofInstance(uncaughtExceptionHandler));
  }

  /**
   * Returns a {@link ExecutorService} that passes uncaught exceptions to
   * a handler returned by Thread.currentThread().getDefaultUncaughtExceptionHandler()
   * at the time the exception is thrown.
   *
   * @see MoreExecutors#exceptionHandlingExecutor(java.util.concurrent.ExecutorService,
   *                                              Thread.UncaughtExceptionHandler)
   * @param executorService delegate
   * @return a decorated executor service
   */
  public static ExecutorService exceptionHandlingExecutor(ExecutorService executorService) {
    return new ExceptionHandlingExecutorService(
        executorService,
        new Supplier<Thread.UncaughtExceptionHandler>() {
          @Override
          public Thread.UncaughtExceptionHandler get() {
            return Thread.currentThread().getUncaughtExceptionHandler();
          }
        });
  }

  /**
   * Returns a {@link ScheduledExecutorService} that passes uncaught exceptions to
   * {@link java.lang.Thread.UncaughtExceptionHandler}.
   * <p>
   * This may be useful because {@link java.util.concurrent.ThreadPoolExecutor} and
   * {@link java.util.concurrent.ScheduledThreadPoolExecutor} provide no built-in propagation of
   * unchecked exceptions thrown from submitted work. Some users are surprised to find that
   * even the default uncaught exception handler is not invoked.
   *
   * @param executorService delegate
   * @param uncaughtExceptionHandler exception handler that will receive exceptions generated
   *                                 from executor tasks.
   * @return a decorated executor service
   */
  public static ScheduledExecutorService exceptionHandlingExecutor(
      ScheduledExecutorService executorService,
      Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {

    Preconditions.checkNotNull(uncaughtExceptionHandler);
    return new ExceptionHandlingScheduledExecutorService(
        executorService,
        Suppliers.ofInstance(uncaughtExceptionHandler));
  }

  /**
   * Returns a {@link ScheduledExecutorService} that passes uncaught exceptions to
   * a handler returned by Thread.currentThread().getDefaultUncaughtExceptionHandler()
   * at the time the exception is thrown.
   *
   * @see MoreExecutors#exceptionHandlingExecutor(java.util.concurrent.ScheduledExecutorService,
   *                                              Thread.UncaughtExceptionHandler)
   * @param executorService delegate
   * @return a decorated executor service
   */
  public static ScheduledExecutorService exceptionHandlingExecutor(
      ScheduledExecutorService executorService) {

    return new ExceptionHandlingScheduledExecutorService(
        executorService,
        new Supplier<Thread.UncaughtExceptionHandler>() {
          @Override
          public Thread.UncaughtExceptionHandler get() {
            return Thread.currentThread().getUncaughtExceptionHandler();
          }
        });
  }
}
