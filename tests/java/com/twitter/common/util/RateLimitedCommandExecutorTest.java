// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.EasyMockTest;
import com.twitter.common.util.RetryingRunnable;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;

import static org.junit.Assert.assertTrue;

/**
 * @author Srinivasan Rajagopal
 */
public class RateLimitedCommandExecutorTest extends EasyMockTest {
  private ScheduledExecutorService taskExecutor;
  private Amount<Long, Time> intervalBetweenRequests;
  private RateLimitedCommandExecutor rateLimiter;
  private ExceptionalCommand exceptionalCommand;
  private BlockingQueue<RetryingRunnable> queue;
  private Runnable queueDrainer;
  private final int numTries = 1;
  private final String name = "name";

  private final class TestException extends Exception { }

  @Before
  public void setUp() throws Exception {
    exceptionalCommand = createMock(ExceptionalCommand.class);
    taskExecutor = createMock(ScheduledExecutorService.class);
    queue = createMock(new Clazz<BlockingQueue<RetryingRunnable>>() {});
    queueDrainer = createMock(Runnable.class);
    intervalBetweenRequests = Amount.of(100L, Time.MILLISECONDS);
  }

  @Test
  public void testFixedRateClientDequeueIsInvoked() throws Exception {
    Capture<Runnable> runnableCapture = createCapture();
    expect(taskExecutor.scheduleWithFixedDelay(
        capture(runnableCapture),
        eq(0L),
        eq((long) intervalBetweenRequests.as(Time.MILLISECONDS)),
        eq(TimeUnit.MILLISECONDS))).andReturn(null);
    control.replay();

    rateLimiter = new RateLimitedCommandExecutor(taskExecutor,
        intervalBetweenRequests,
        queueDrainer,
        queue);
    assertTrue(runnableCapture.hasCaptured());
    assertTrue(runnableCapture.getValue() instanceof Runnable);
  }

  @Test
  public void testEnqueue() throws Exception {
    Capture<Runnable> runnableCapture = createCapture();
    expect(taskExecutor.scheduleWithFixedDelay((Runnable) anyObject(),
        eq(0L),
        eq((long) intervalBetweenRequests.as(Time.MILLISECONDS)),
        eq(TimeUnit.MILLISECONDS))).andReturn(null);

    Capture<RetryingRunnable> runnableTaskCapture = createCapture();
    expect(queue.add(capture(runnableTaskCapture))).andReturn(true);
    control.replay();
    rateLimiter = new RateLimitedCommandExecutor(taskExecutor,
        intervalBetweenRequests,
        queueDrainer,
        queue);
    rateLimiter.execute(name, exceptionalCommand, TestException.class,
        numTries, intervalBetweenRequests);
    assertTrue(runnableTaskCapture.hasCaptured());
  }

  @Test
  public void testDrainQueueCommandHandlesException() {
    Capture<Runnable> runnableCapture = createCapture();
    expect(taskExecutor.scheduleWithFixedDelay(
        capture(runnableCapture),
        eq(0L),
        eq((long) intervalBetweenRequests.as(Time.MILLISECONDS)),
        eq(TimeUnit.MILLISECONDS))).andReturn(null);
    queueDrainer.run();
    expectLastCall().andThrow(new RuntimeException());
    control.replay();
    rateLimiter = new RateLimitedCommandExecutor(taskExecutor,
        intervalBetweenRequests,
        queueDrainer,
        queue);

    //Execute the runnable to ensure the exception does not propagate
    // and potentially kill threads in the executor service.
    runnableCapture.getValue().run();
  }
}
