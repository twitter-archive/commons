package com.twitter.common.util.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;

/**
 * A scheduled executor service that delegates to another executor service, invoking an uncaught
 * exception handler if any exceptions are thrown in submitted work.
 *
 * @see MoreExecutors
 */
class ExceptionHandlingScheduledExecutorService
    extends ForwardingExecutorService<ScheduledExecutorService>
    implements ScheduledExecutorService {
  private final Supplier<Thread.UncaughtExceptionHandler> handler;

  /**
   * Construct a {@link ScheduledExecutorService} with a supplier of
   * {@link Thread.UncaughtExceptionHandler} that handles exceptions thrown from submitted work.
   */
  ExceptionHandlingScheduledExecutorService(
      ScheduledExecutorService delegate,
      Supplier<Thread.UncaughtExceptionHandler> handler) {
    super(delegate);
    this.handler = handler;
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
    return delegate.schedule(TaskConverter.alertingRunnable(runnable, handler), delay, timeUnit);
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit timeUnit) {
    return delegate.schedule(TaskConverter.alertingCallable(callable, handler), delay, timeUnit);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable runnable, long initialDelay, long period, TimeUnit timeUnit) {
    return delegate.scheduleAtFixedRate(
        TaskConverter.alertingRunnable(runnable, handler), initialDelay, period, timeUnit);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable runnable, long initialDelay, long delay, TimeUnit timeUnit) {
    return delegate.scheduleWithFixedDelay(
        TaskConverter.alertingRunnable(runnable, handler), initialDelay, delay, timeUnit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return delegate.submit(TaskConverter.alertingCallable(task, handler));
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return delegate.submit(TaskConverter.alertingRunnable(task, handler), result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return delegate.submit(TaskConverter.alertingRunnable(task, handler));
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return delegate.invokeAll(TaskConverter.alertingCallables(tasks, handler));
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return delegate.invokeAll(TaskConverter.alertingCallables(tasks, handler), timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return delegate.invokeAny(TaskConverter.alertingCallables(tasks, handler));
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(TaskConverter.alertingCallables(tasks, handler), timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    delegate.execute(TaskConverter.alertingRunnable(command, handler));
  }
}
