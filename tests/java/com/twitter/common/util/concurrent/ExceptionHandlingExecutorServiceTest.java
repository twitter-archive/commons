package com.twitter.common.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.testing.TearDown;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ExceptionHandlingExecutorServiceTest extends EasyMockTest {
  private static final RuntimeException EXCEPTION = new RuntimeException();

  private ExecutorService executorService;
  private Thread.UncaughtExceptionHandler signallingHandler;

  @Before
  public void setUp() throws Exception {
    signallingHandler = createMock(Thread.UncaughtExceptionHandler.class);
    executorService = MoreExecutors.exceptionHandlingExecutor(
        Executors.newCachedThreadPool(new ThreadFactoryBuilder()
            .setNameFormat("ExceptionHandlingExecutorServiceTest-%d")
            .build()),
        signallingHandler);

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
    Runnable runnable = createMock(Runnable.class);
    runnable.run();

    control.replay();

    executorService.submit(runnable).get();
  }

  @Test
  public void testSubmitFailingRunnable() throws Exception {
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
    Integer returnValue = 123;
    Callable<Integer> callable = createMock(new Clazz<Callable<Integer>>() {});
    callable.call();
    expectLastCall().andReturn(returnValue);

    control.replay();

    assertEquals(returnValue, executorService.submit(callable).get());
  }

  @Test
  public void testSubmitFailingCallable() throws Exception {
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

  @Test(expected = NullPointerException.class)
  public void testNullHandler() throws Exception {
    control.replay();
    MoreExecutors.exceptionHandlingExecutor(executorService, null);
  }
}
