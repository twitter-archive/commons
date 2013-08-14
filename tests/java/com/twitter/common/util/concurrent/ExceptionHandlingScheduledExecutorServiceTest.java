package com.twitter.common.util.concurrent;

import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.NullPointerException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.testing.TearDown;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.util.concurrent.MoreExecutors;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ExceptionHandlingScheduledExecutorServiceTest extends EasyMockTest {
  private static final RuntimeException EXCEPTION = new RuntimeException();

  private ScheduledExecutorService executorService;
  private Thread.UncaughtExceptionHandler signallingHandler;

  @Before
  public void setUp() throws Exception {
    signallingHandler = createMock(Thread.UncaughtExceptionHandler.class);
    executorService = MoreExecutors.exceptionHandlingExecutor(
        Executors.newSingleThreadScheduledExecutor(), signallingHandler);

    final ExecutorServiceShutdown executorServiceShutdown = new ExecutorServiceShutdown(
        executorService, Amount.of(3L, Time.SECONDS));

    addTearDown(new TearDown() {
      @Override
      public void tearDown() throws Exception {
        executorServiceShutdown.execute();
      }
    });
  }

  @Test
  public void testSubmitRunnable() throws Exception {
    signallingHandler.uncaughtException(anyObject(Thread.class), eq(EXCEPTION));
    Runnable runnable = createMock(Runnable.class);
    runnable.run();
    expectLastCall().andThrow(EXCEPTION);

    control.replay();

    try {
      executorService.submit(runnable).get();
      fail(EXCEPTION.getClass().getSimpleName() + " should be thrown.");
    } catch (ExecutionException e) {
      assertEquals(EXCEPTION, e.getCause());
    }
  }

  @Test
  public void testSubmitCallable() throws Exception {
    signallingHandler.uncaughtException(anyObject(Thread.class), eq(EXCEPTION));
    Callable<Void> callable = createMock(new Clazz<Callable<Void>>() {});
    expect(callable.call()).andThrow(EXCEPTION);

    control.replay();

    try {
      executorService.submit(callable).get();
      fail(EXCEPTION.getClass().getSimpleName() + " should be thrown.");
    } catch (ExecutionException e) {
      assertEquals(EXCEPTION, e.getCause());
    }
  }

  @Test
  public void testScheduleAtFixedRate() throws Exception {
    signallingHandler.uncaughtException(anyObject(Thread.class), eq(EXCEPTION));
    Runnable runnable = createMock(Runnable.class);
    runnable.run();
    expectLastCall().andThrow(EXCEPTION);

    control.replay();

    try {
      executorService.scheduleAtFixedRate(runnable, 0, 10, TimeUnit.MILLISECONDS).get();
      fail(EXCEPTION.getClass().getSimpleName() + " should be thrown.");
    } catch (ExecutionException e) {
      assertEquals(EXCEPTION, e.getCause());
    }
  }

  @Test
  public void testScheduleWithFixedDelay() throws Exception {
    signallingHandler.uncaughtException(anyObject(Thread.class), eq(EXCEPTION));
    Runnable runnable = createMock(Runnable.class);
    runnable.run();
    expectLastCall().andThrow(EXCEPTION);

    control.replay();

    try {
      executorService.scheduleWithFixedDelay(runnable, 0, 10, TimeUnit.MILLISECONDS).get();
      fail(EXCEPTION.getClass().getSimpleName() + " should be thrown.");
    } catch (ExecutionException e) {
      assertEquals(EXCEPTION, e.getCause());
    }
  }

  @Test(expected = NullPointerException.class)
  public void testNullHandler() throws Exception {
    control.replay();
    MoreExecutors.exceptionHandlingExecutor(executorService, null);
  }
}
