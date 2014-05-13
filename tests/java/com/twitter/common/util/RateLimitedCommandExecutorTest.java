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

import com.twitter.common.base.Command;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Srinivasan Rajagopal
 */
public class RateLimitedCommandExecutorTest extends EasyMockTest {
  private ScheduledExecutorService taskExecutor;
  private Amount<Long, Time> intervalBetweenRequests;
  private RateLimitedCommandExecutor rateLimiter;
  private Command command;
  private BlockingQueue<RetryingRunnable<?>> queue;
  private Runnable queueDrainer;

  @Before
  public void setUp() throws Exception {
    command = createMock(Command.class);
    taskExecutor = createMock(ScheduledExecutorService.class);
    queue = createMock(new Clazz<BlockingQueue<RetryingRunnable<?>>>() {});
    queueDrainer = createMock(Runnable.class);
    intervalBetweenRequests = Amount.of(100L, Time.MILLISECONDS);
  }

  private RateLimitedCommandExecutor createLimiter() {
    return new RateLimitedCommandExecutor(
        taskExecutor,
        intervalBetweenRequests,
        queueDrainer,
        queue);
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

    rateLimiter = createLimiter();
    assertTrue(runnableCapture.hasCaptured());
    assertNotNull(runnableCapture.getValue());
  }

  @Test
  public void testEnqueue() throws Exception {
    expect(taskExecutor.scheduleWithFixedDelay((Runnable) anyObject(),
        eq(0L),
        eq((long) intervalBetweenRequests.as(Time.MILLISECONDS)),
        eq(TimeUnit.MILLISECONDS))).andReturn(null);

    Capture<RetryingRunnable> runnableTaskCapture = createCapture();
    expect(queue.add(capture(runnableTaskCapture))).andReturn(true);
    control.replay();

    rateLimiter = createLimiter();
    rateLimiter.execute("name", command, RuntimeException.class, 1, intervalBetweenRequests);
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
    rateLimiter = createLimiter();

    //Execute the runnable to ensure the exception does not propagate
    // and potentially kill threads in the executor service.
    runnableCapture.getValue().run();
  }
}
