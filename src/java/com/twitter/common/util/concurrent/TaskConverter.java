package com.twitter.common.util.concurrent;

import java.util.Collection;
import java.util.concurrent.Callable;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;

final class TaskConverter {
  private TaskConverter() {
    // utility
  }

  /**
   * Returns a wrapped {@link Runnable} that passes uncaught exceptions thrown from the
   * original Runnable to {@link Thread.UncaughtExceptionHandler}.
   *
   * @param runnable runnable to be wrapped
   * @param handler exception handler that will receive exceptions generated in the runnable
   * @return wrapped runnable
   */
  static Runnable alertingRunnable(
      final Runnable runnable,
      final Supplier<Thread.UncaughtExceptionHandler> handler) {

    return new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        } catch (Throwable t) {
          handler.get().uncaughtException(Thread.currentThread(), t);
          throw Throwables.propagate(t);
        }
      }
    };
  }

  /**
   * Returns a wrapped {@link java.util.concurrent.Callable} that passes uncaught exceptions
   * thrown from the original Callable to {@link Thread.UncaughtExceptionHandler}.
   *
   * @param callable callable to be wrapped
   * @param handler exception handler that will receive exceptions generated in the callable
   * @return wrapped callable
   */
  static <V> Callable<V> alertingCallable(
      final Callable<V> callable,
      final Supplier<Thread.UncaughtExceptionHandler> handler) {

    return new Callable<V>() {
      @Override
      public V call() throws Exception {
        try {
          return callable.call();
        } catch (Throwable t) {
          handler.get().uncaughtException(Thread.currentThread(), t);
          throw Throwables.propagate(t);
        }
      }
    };
  }

  /*
   * Calls #alertingCallable on a collection of callables
   */
  static <V> Collection<? extends Callable<V>> alertingCallables(
      Collection<? extends Callable<V>> callables,
      final Supplier<Thread.UncaughtExceptionHandler> handler) {

    return Collections2.transform(callables, new Function<Callable<V>, Callable<V>>() {
      @Override
      public Callable<V> apply(Callable<V> callable) {
        return alertingCallable(callable, handler);
      }
    });
  }
}
