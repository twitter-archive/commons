// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.thrift;

import com.google.common.base.Function;
import com.twitter.common.base.Command;
import com.twitter.common.thrift.testing.MockTSocket;
import com.twitter.common.net.pool.Connection;
import com.twitter.common.net.pool.ObjectPool;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.Stat;
import com.twitter.common.stats.Stats;
import com.twitter.common.thrift.callers.RetryingCaller;
import com.twitter.common.net.loadbalancing.LoadBalancer;
import com.twitter.common.net.loadbalancing.RequestTracker;
import com.twitter.common.util.concurrent.ForwardingExecutorService;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IExpectationSetters;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.and;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class ThriftTest {

  private static final Amount<Long, Time> ASYNC_CONNECT_TIMEOUT = Amount.of(1L, Time.SECONDS);

  public static class NotFoundException extends Exception {}

  public interface TestService {
    int calculateMass(String profileName) throws NotFoundException, TException;
  }

  public interface TestServiceAsync {
    void calculateMass(String profileName, AsyncMethodCallback callback) throws TException;
  }

  private IMocksControl control;
  private ObjectPool<Connection<TTransport, InetSocketAddress>> connectionPool;
  private Function<TTransport, TestService> clientFactory;
  private Function<TTransport, TestServiceAsync> asyncClientFactory;
  private RequestTracker<InetSocketAddress> requestTracker;

  private AsyncMethodCallback<Integer> callback;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() throws Exception {
    control = EasyMock.createControl();

    this.connectionPool = control.createMock(ObjectPool.class);
    this.clientFactory = control.createMock(Function.class);
    this.asyncClientFactory = control.createMock(Function.class);
    this.requestTracker = control.createMock(LoadBalancer.class);

    this.callback = control.createMock(AsyncMethodCallback.class);
  }

  @After
  public void after() {
    Stats.flush();
  }

  @Test
  public void testDoCallNoDeadline() throws Exception {
    TestService testService = expectServiceCall(false);
    expect(testService.calculateMass("jake")).andReturn(42);
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.SUCCESS), anyLong());

    Thrift<TestService> thrift = createThrift(expectUnusedExecutorService());

    control.replay();

    int userMass = thrift.builder().blocking().create().calculateMass("jake");

    assertEquals(42, userMass);

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 0);
    assertReconnectsTotal(thrift, 0);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testDoCallAsync() throws Exception {
    // Capture the callback that Thift has wrapped around our callback.
    Capture<AsyncMethodCallback<Integer>> callbackCapture =
        new Capture<AsyncMethodCallback<Integer>>();
    expectAsyncServiceCall(false).calculateMass(eq("jake"), capture(callbackCapture));
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.SUCCESS), anyLong());

    // Verifies that our callback was called.
    callback.onComplete(42);

    Thrift<TestServiceAsync> thrift = createAsyncThrift(expectUnusedExecutorService());

    control.replay();

    thrift.builder().withConnectTimeout(ASYNC_CONNECT_TIMEOUT).create()
        .calculateMass("jake", callback);

    // Mimicks the async response from the server.
    callbackCapture.getValue().onComplete(42);

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 0);
    assertReconnectsTotal(thrift, 0);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testDoCallServiceException() throws Exception {
    TestService testService = expectServiceCall(true);
    NotFoundException notFoundException = new NotFoundException();
    expect(testService.calculateMass("jake")).andThrow(notFoundException);
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    Thrift<TestService> thrift = createThrift(expectUnusedExecutorService());

    control.replay();

    try {
      thrift.builder().blocking().create().calculateMass("jake");
      fail("Expected service custom exception to bubble unmodified");
    } catch (NotFoundException e) {
      assertSame(notFoundException, e);
    }

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 1);
    assertReconnectsTotal(thrift, 1);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testDoCallAsyncServiceException() throws Exception {
    NotFoundException notFoundException = new NotFoundException();

    // Capture the callback that Thift has wrapped around our callback.
    Capture<AsyncMethodCallback<Integer>> callbackCapture =
        new Capture<AsyncMethodCallback<Integer>>();
    expectAsyncServiceCall(true).calculateMass(eq("jake"), capture(callbackCapture));
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // Verifies that our callback was called.
    callback.onError(notFoundException);

    Thrift<TestServiceAsync> thrift = createAsyncThrift(expectUnusedExecutorService());

    control.replay();

    thrift.builder().withConnectTimeout(ASYNC_CONNECT_TIMEOUT).create()
        .calculateMass("jake", callback);

    // Mimicks the async response from the server.
    callbackCapture.getValue().onError(notFoundException);

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 1);
    assertReconnectsTotal(thrift, 1);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testDoCallThriftException() throws Exception {
    Capture<TTransport> transportCapture = new Capture<TTransport>();
    TestService testService = expectThriftError(transportCapture);
    TTransportException tException = new TTransportException();
    expect(testService.calculateMass("jake")).andThrow(tException);
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    Thrift<TestService> thrift = createThrift(expectUnusedExecutorService());

    control.replay();

    try {
      thrift.builder().blocking().create().calculateMass("jake");
      fail("Expected thrift exception to bubble unmodified");
    } catch (TException e) {
      assertSame(tException, e);
    }

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 1);
    assertReconnectsTotal(thrift, 1);
    assertTimeoutsTotal(thrift, 0);

    assertTrue(transportCapture.hasCaptured());
    assertFalse("Expected the transport to be forcibly closed when a thrift error is encountered",
        transportCapture.getValue().isOpen());

    control.verify();
  }

  @Test
  public void doCallAsyncThriftException() throws Exception {
    TTransportException tException = new TTransportException();

    expectAsyncServiceCall(true).calculateMass(eq("jake"), (AsyncMethodCallback) anyObject());
    expectLastCall().andThrow(tException);
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    Thrift<TestServiceAsync> thrift = createAsyncThrift(expectUnusedExecutorService());

    callback.onError(tException);

    control.replay();

    thrift.builder().withConnectTimeout(ASYNC_CONNECT_TIMEOUT).create()
        .calculateMass("jake", callback);

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 1);
    assertReconnectsTotal(thrift, 1);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDisallowsAsyncWithDeadline() {
    Config config = Config.builder()
        .withRequestTimeout(Amount.of(1L, Time.SECONDS))
        .create();

    new Thrift<TestServiceAsync>(config, connectionPool, requestTracker,
      "foo", TestServiceAsync.class, asyncClientFactory, true).create();
  }

  @Test
  public void testDoCallDeadlineMet() throws Exception {
    TestService testService = expectServiceCall(false);
    expect(testService.calculateMass("jake")).andReturn(42);
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.SUCCESS), anyLong());

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Thrift<TestService> thrift = createThrift(executorService);

    control.replay();

    int userMass = thrift.builder().withRequestTimeout(Amount.of(1L, Time.DAYS)).create()
        .calculateMass("jake");

    assertEquals(42, userMass);

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 0);
    assertReconnectsTotal(thrift, 0);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  @Ignore("Flaky: https://trac.twitter.com/twttr/ticket/11474")
  public void testDoCallDeadlineExpired() throws Exception {
    TestService testService = expectServiceCall(true);

    // Setup a way to verify the callable was cancelled by Thrift when timeout elapsed
    final CountDownLatch remoteCallComplete = new CountDownLatch(1);
    final CountDownLatch remoteCallStarted = new CountDownLatch(1);
    final Command verifyCancelled = control.createMock(Command.class);
    verifyCancelled.execute();
    final Object block = new Object();
    expect(testService.calculateMass("jake")).andAnswer(new IAnswer<Integer>() {
      @Override public Integer answer() throws TException {
        try {
          synchronized (block) {
            remoteCallStarted.countDown();
            block.wait();
          }
          fail("Expected late work to be cancelled and interrupted");
        } catch (InterruptedException e) {
          verifyCancelled.execute();
        } finally {
          remoteCallComplete.countDown();
        }
        throw new TTransportException();
      }
    });
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.TIMEOUT), anyLong());

    ExecutorService executorService =
        new ForwardingExecutorService<ExecutorService>(Executors.newSingleThreadExecutor()) {
          @Override public <T> Future<T> submit(Callable<T> task) {
            Future<T> future = super.submit(task);

            // make sure the task is started so we can verify it gets cancelled
            try {
              remoteCallStarted.await();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }

            return future;
          }
        };
    Thrift<TestService> thrift = createThrift(executorService);

    control.replay();

    try {
      thrift.builder().withRequestTimeout(Amount.of(1L, Time.NANOSECONDS)).create()
          .calculateMass("jake");
      fail("Expected a timeout");
    } catch (TTimeoutException e) {
      // expected
    } finally {
      remoteCallComplete.await();
    }

    assertRequestsTotal(thrift, 0);
    assertErrorsTotal(thrift, 0);
    assertReconnectsTotal(thrift, 0);
    assertTimeoutsTotal(thrift, 1);

    control.verify();
  }

  @Test
  public void testRetriesNoProblems() throws Exception {
    expect(expectServiceCall(false).calculateMass("jake")).andReturn(42);
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.SUCCESS), anyLong());

    Thrift<TestService> thrift = createThrift(expectUnusedExecutorService());

    control.replay();

    TestService testService = thrift.builder().blocking().withRetries(1).create();

    assertEquals(42, testService.calculateMass("jake"));

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 0);
    assertReconnectsTotal(thrift, 0);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testAsyncRetriesNoProblems() throws Exception {
    // Capture the callback that Thift has wrapped around our callback.
    Capture<AsyncMethodCallback<Integer>> callbackCapture =
        new Capture<AsyncMethodCallback<Integer>>();
    expectAsyncServiceCall(false).calculateMass(eq("jake"), capture(callbackCapture));
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.SUCCESS), anyLong());

    // Verifies that our callback was called.
    callback.onComplete(42);

    Thrift<TestServiceAsync> thrift = createAsyncThrift(expectUnusedExecutorService());

    control.replay();

    thrift.builder().withRetries(1).withConnectTimeout(ASYNC_CONNECT_TIMEOUT).create()
        .calculateMass("jake", callback);

    // Mimicks the async response from the server.
    callbackCapture.getValue().onComplete(42);

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 0);
    assertReconnectsTotal(thrift, 0);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testRetriesRecover() throws Exception {
    // 1st call
    expect(expectServiceCall(true).calculateMass("jake")).andThrow(new TTransportException());
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // 1st retry recovers
    expect(expectServiceCall(false).calculateMass("jake")).andReturn(42);
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.SUCCESS), anyLong());

    Thrift<TestService> thrift = createThrift(expectUnusedExecutorService());

    control.replay();

    TestService testService = thrift.builder().blocking().withRetries(1).create();

    assertEquals(42, testService.calculateMass("jake"));

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 0);
    assertReconnectsTotal(thrift, 0);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testAsyncRetriesRecover() throws Exception {
    // Capture the callback that Thift has wrapped around our callback.
    Capture<AsyncMethodCallback<Integer>> callbackCapture =
        new Capture<AsyncMethodCallback<Integer>>();

    // 1st call
    expectAsyncServiceCall(true).calculateMass(eq("jake"), capture(callbackCapture));
    expectLastCall().andThrow(new TTransportException());
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // 1st retry recovers
    expectAsyncServiceRetry(false).calculateMass(eq("jake"), capture(callbackCapture));
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.SUCCESS), anyLong());

    // Verifies that our callback was called.
    callback.onComplete(42);

    Thrift<TestServiceAsync> thrift = createAsyncThrift(expectUnusedExecutorService());

    control.replay();

    thrift.builder().withRetries(1).withConnectTimeout(ASYNC_CONNECT_TIMEOUT).create()
        .calculateMass("jake", callback);

    // Mimicks the async response from the server.
    callbackCapture.getValue().onComplete(42);

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 0);
    assertReconnectsTotal(thrift, 0);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testRetriesFailure() throws Exception {
    // 1st call
    expect(expectServiceCall(true).calculateMass("jake")).andThrow(new TTransportException());
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // 1st retry
    expect(expectServiceCall(true).calculateMass("jake")).andThrow(new TTransportException());
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // 2nd retry
    TTransportException finalRetryException = new TTransportException();
    expect(expectServiceCall(true).calculateMass("jake")).andThrow(finalRetryException);
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    Thrift<TestService> thrift = createThrift(expectUnusedExecutorService());

    control.replay();

    TestService testService = thrift.builder().blocking().withRetries(2).create();

    try {
      testService.calculateMass("jake");
      fail("Expected an exception to be thrown since all retires failed");
    } catch (TException e) {
      assertSame(finalRetryException, e);
    }

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 1);
    assertReconnectsTotal(thrift, 1);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testAsyncRetriesFailure() throws Exception {
    // 1st call
    Capture<AsyncMethodCallback<Integer>> callbackCapture1 =
        new Capture<AsyncMethodCallback<Integer>>();
    expectAsyncServiceCall(true).calculateMass(eq("jake"), capture(callbackCapture1));
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // 1st retry
    Capture<AsyncMethodCallback<Integer>> callbackCapture2 =
        new Capture<AsyncMethodCallback<Integer>>();
    expectAsyncServiceRetry(true).calculateMass(eq("jake"), capture(callbackCapture2));
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // 2nd retry
    Capture<AsyncMethodCallback<Integer>> callbackCapture3 =
        new Capture<AsyncMethodCallback<Integer>>();
    expectAsyncServiceRetry(true).calculateMass(eq("jake"), capture(callbackCapture3));
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // Verifies that our callback was called.
    TTransportException returnedException = new TTransportException();
    callback.onError(returnedException);

    Thrift<TestServiceAsync> thrift = createAsyncThrift(expectUnusedExecutorService());

    control.replay();

    thrift.builder().withRetries(2).withConnectTimeout(ASYNC_CONNECT_TIMEOUT).create()
        .calculateMass("jake", callback);

    callbackCapture1.getValue().onError(new TTransportException());
    callbackCapture2.getValue().onError(new IOException());
    callbackCapture3.getValue().onError(returnedException);

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 1);
    assertReconnectsTotal(thrift, 1);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testRetrySelection() throws Exception {
    expect(expectServiceCall(true).calculateMass("jake")).andThrow(new NotFoundException());
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // verify subclasses pass the retry filter
    class HopelesslyLost extends NotFoundException {}
    expect(expectServiceCall(true).calculateMass("jake")).andThrow(new HopelesslyLost());
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    TTransportException nonRetryableException = new TTransportException();
    expect(expectServiceCall(true).calculateMass("jake")).andThrow(nonRetryableException);
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    Thrift<TestService> thrift = createThrift(expectUnusedExecutorService());

    control.replay();

    TestService testService =
        thrift.builder().blocking().withRetries(2).retryOn(NotFoundException.class).create();

    try {
      testService.calculateMass("jake");
      fail("Expected n exception to be thrown since all retires failed");
    } catch (TException e) {
      assertSame(nonRetryableException, e);
    }

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 1);
    assertReconnectsTotal(thrift, 1);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testAsyncRetrySelection() throws Exception {
    // verify subclasses pass the retry filter
    class HopelesslyLost extends NotFoundException {}
    Capture<AsyncMethodCallback<Integer>> callbackCapture1 =
        new Capture<AsyncMethodCallback<Integer>>();
    expectAsyncServiceCall(true).calculateMass(eq("jake"), capture(callbackCapture1));
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    Capture<AsyncMethodCallback<Integer>> callbackCapture2 =
        new Capture<AsyncMethodCallback<Integer>>();
    expectAsyncServiceRetry(true).calculateMass(eq("jake"), capture(callbackCapture2));
    requestTracker.requestResult(
        (InetSocketAddress) anyObject(), eq(RequestTracker.RequestResult.FAILED), anyLong());

    // Verifies that our callback was called.
    TTransportException nonRetryableException = new TTransportException();
    callback.onError(nonRetryableException);

    Thrift<TestServiceAsync> thrift = createAsyncThrift(expectUnusedExecutorService());

    control.replay();

    TestServiceAsync testService = thrift.builder()
        .withRetries(2)
        .retryOn(NotFoundException.class)
        .withConnectTimeout(ASYNC_CONNECT_TIMEOUT).create();

    testService.calculateMass("jake", callback);
    callbackCapture1.getValue().onError(new HopelesslyLost());
    callbackCapture2.getValue().onError(nonRetryableException);

    assertRequestsTotal(thrift, 1);
    assertErrorsTotal(thrift, 1);
    assertReconnectsTotal(thrift, 1);
    assertTimeoutsTotal(thrift, 0);

    control.verify();
  }

  @Test
  public void testResourceExhausted() throws Exception {
    expectConnectionPoolResourceExhausted(Config.DEFAULT_CONNECT_TIMEOUT);
    Thrift<TestService> thrift = createThrift(expectUnusedExecutorService());

    control.replay();

    TestService testService = thrift.builder().blocking().create();

    try {
      testService.calculateMass("jake");
      fail("Expected a TResourceExhaustedException.");
    } catch (TResourceExhaustedException e) {
      // Expected
    }

    control.verify();
  }

  @Test
  public void testAsyncResourceExhausted() throws Exception {
    expectConnectionPoolResourceExhausted(ASYNC_CONNECT_TIMEOUT);
    Thrift<TestServiceAsync> thrift = createAsyncThrift(expectUnusedExecutorService());

    callback.onError((Throwable) and(anyObject(), isA(TResourceExhaustedException.class)));

    control.replay();

    TestServiceAsync testService = thrift.builder().withConnectTimeout(ASYNC_CONNECT_TIMEOUT)
        .create();

    testService.calculateMass("jake", callback);

    control.verify();
  }

  @Test
  public void testAsyncDoesNotRetryResourceExhausted() throws Exception {
    expect(connectionPool.get(ASYNC_CONNECT_TIMEOUT)).andThrow(
        new ResourceExhaustedException("first"));

    Thrift<TestServiceAsync> thrift = createAsyncThrift(expectUnusedExecutorService());

    callback.onError((Throwable) and(anyObject(), isA(TResourceExhaustedException.class)));

    control.replay();

    thrift.builder().withRetries(1).withConnectTimeout(ASYNC_CONNECT_TIMEOUT).create()
        .calculateMass("jake", callback);

    control.verify();
  }

  @Test
  public void testConnectionPoolTimeout() throws Exception {
    expectConnectionPoolTimeout(Config.DEFAULT_CONNECT_TIMEOUT);
    Thrift<TestService> thrift = createThrift(expectUnusedExecutorService());

    control.replay();

    TestService testService =
        thrift.builder().blocking().create();

    try {
      testService.calculateMass("jake");
      fail("Expected a TTimeoutException.");
    } catch (TTimeoutException e) {
      // Expected
    }

    control.verify();
  }

  @Test
  public void testDoCallDeadlineNoThreads() throws Exception {
    control.replay();

    ExecutorService executorService =
        new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>());

    Thrift<TestService> thrift = createThrift(executorService);

    final TestService service =
          thrift.builder().noRetries().withRequestTimeout(Amount.of(1L, Time.SECONDS)).create();

    final CountDownLatch remoteCallComplete = new CountDownLatch(1);
    final CountDownLatch remoteCallStarted = new CountDownLatch(1);

    Future<Integer> result = executorService.submit(new Callable<Integer>() {
      @Override public Integer call() throws Exception {
        remoteCallStarted.countDown();
        remoteCallComplete.await();
        return service.calculateMass("jake");
      }
    });

    remoteCallStarted.await();
    try {
      service.calculateMass("jake");
      fail("Expected no available threads to trigger resource exhausted");
    } catch (TResourceExhaustedException e) {
      // expected
    } finally {
      remoteCallComplete.countDown();
    }

    try {
      result.get();
      fail("Expected no available threads to trigger resource exhausted");
    } catch (ExecutionException e) {
      assertEquals(TResourceExhaustedException.class, e.getCause().getClass());
    }

    control.verify();
  }

  private ExecutorService expectUnusedExecutorService() {
    return control.createMock(ExecutorService.class);
  }

  private static final String STAT_REQUESTS = "requests_events";
  private static final String STAT_ERRORS = "errors";
  private static final String STAT_RECONNECTS = "reconnects";
  private static final String STAT_TIMEOUTS = "timeouts";

  private void assertRequestsTotal(Thrift<?> thrift, int total) {
    assertRequestStatValue(STAT_REQUESTS, total);
  }

  private void assertErrorsTotal(Thrift<?> thrift, int total) {
    assertRequestStatValue(STAT_ERRORS, total);
  }

  private void assertReconnectsTotal(Thrift<?> thrift, int total) {
    assertRequestStatValue(STAT_RECONNECTS, total);
  }

  private void assertTimeoutsTotal(Thrift<?> thrift, int total) {
    assertRequestStatValue(STAT_TIMEOUTS, total);
  }

  private void assertRequestStatValue(String statName, long expectedValue) {

    Stat<Long> var = Stats.getVariable("foo_calculateMass_" + statName);

    assertNotNull(var);
    assertEquals(expectedValue, (long) var.read());
  }

  private Thrift<TestService> createThrift(ExecutorService executorService) {
    return new Thrift<TestService>(executorService, connectionPool, requestTracker, "foo",
        TestService.class, clientFactory, false);
  }

  private Thrift<TestServiceAsync> createAsyncThrift(ExecutorService executorService) {
    return new Thrift<TestServiceAsync>(executorService, connectionPool, requestTracker, "foo",
        TestServiceAsync.class, asyncClientFactory, true);
  }

  private TestService expectServiceCall(boolean withFailure)
      throws ResourceExhaustedException, TimeoutException {
    Connection<TTransport, InetSocketAddress> connection = expectConnectionPoolGet();
    return expectServiceCall(connection, withFailure);
  }

  private TestServiceAsync expectAsyncServiceCall(boolean withFailure)
      throws ResourceExhaustedException, TimeoutException {
    return expectAsyncServiceCall(expectConnectionPoolGet(ASYNC_CONNECT_TIMEOUT), withFailure);
  }

  private TestServiceAsync expectAsyncServiceRetry(boolean withFailure)
      throws ResourceExhaustedException, TimeoutException {
    return expectAsyncServiceCall(
        expectConnectionPoolGet(RetryingCaller.NONBLOCKING_TIMEOUT), withFailure);
  }

  private TestService expectThriftError(Capture<TTransport> transportCapture)
      throws ResourceExhaustedException, TimeoutException {
    Connection<TTransport, InetSocketAddress> connection = expectConnectionPoolGet();
    return expectServiceCall(connection, transportCapture, true);
  }

  private Connection<TTransport, InetSocketAddress> expectConnectionPoolGet()
      throws ResourceExhaustedException, TimeoutException {
    Connection<TTransport, InetSocketAddress> connection = createConnection();
    expect(connectionPool.get(Config.DEFAULT_CONNECT_TIMEOUT)).andReturn(connection);
    return connection;
  }

  private Connection<TTransport, InetSocketAddress> expectConnectionPoolGet(
      Amount<Long, Time> timeout) throws ResourceExhaustedException, TimeoutException {
    Connection<TTransport, InetSocketAddress> connection = createConnection();
    expect(connectionPool.get(timeout)).andReturn(connection);
    return connection;
  }

  private void expectConnectionPoolResourceExhausted(Amount<Long, Time> timeout)
      throws ResourceExhaustedException, TimeoutException {
    expect(connectionPool.get(timeout)).andThrow(new ResourceExhaustedException(""));
  }

  private void expectConnectionPoolTimeout(Amount<Long, Time> timeout)
      throws ResourceExhaustedException, TimeoutException {
    expect(connectionPool.get(timeout)).andThrow(new TimeoutException());
  }

  private Connection<TTransport, InetSocketAddress> createConnection() {
    return new TTransportConnection(new MockTSocket(),
        InetSocketAddress.createUnresolved(MockTSocket.HOST, MockTSocket.PORT));
  }

  private TestService expectServiceCall(Connection<TTransport, InetSocketAddress> connection,
      boolean withFailure) {
    return expectServiceCall(connection, null, withFailure);
  }

  private TestServiceAsync expectAsyncServiceCall(
      Connection<TTransport, InetSocketAddress> connection, boolean withFailure) {
    return expectAsyncServiceCall(connection, null, withFailure);
  }

  private TestService expectServiceCall(Connection<TTransport, InetSocketAddress> connection,
      Capture<TTransport> transportCapture, boolean withFailure) {

    TestService testService = control.createMock(TestService.class);
    if (connection != null) {
      IExpectationSetters<TestService> expectApply = transportCapture == null
          ? expect(clientFactory.apply(EasyMock.isA(TTransport.class)))
          : expect(clientFactory.apply(EasyMock.capture(transportCapture)));
      expectApply.andReturn(testService);

      if (withFailure) {
        connectionPool.remove(connection);
      } else {
        connectionPool.release(connection);
      }
    }
    return testService;
  }

  private TestServiceAsync expectAsyncServiceCall(
      Connection<TTransport, InetSocketAddress> connection,
      Capture<TTransport> transportCapture, boolean withFailure) {

    TestServiceAsync testService = control.createMock(TestServiceAsync.class);
    if (connection != null) {
      IExpectationSetters<TestServiceAsync> expectApply = transportCapture == null
          ? expect(asyncClientFactory.apply(EasyMock.isA(TTransport.class)))
          : expect(asyncClientFactory.apply(EasyMock.capture(transportCapture)));
      expectApply.andReturn(testService);

      if (withFailure) {
        connectionPool.remove(connection);
      } else {
        connectionPool.release(connection);
      }
    }
    return testService;
  }
}
