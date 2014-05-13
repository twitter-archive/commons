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
import java.util.concurrent.Executor;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

/**
 * @author Srinivasan Rajagopal
 */
public class QueueDrainerTest extends EasyMockTest {
  private Executor taskExecutor;
  private BlockingQueue<RetryingRunnable> blockingQueue;
  private QueueDrainer queueDrainer;

  @Before
  public void setUp() {
    taskExecutor = createMock(Executor.class);
    blockingQueue = createMock(new Clazz<BlockingQueue<RetryingRunnable>>() { });
    queueDrainer = new QueueDrainer<RetryingRunnable>(taskExecutor, blockingQueue);
  }

  @Test
  public void testDrainsQueue() throws Exception {
    RetryingRunnable task = createMock(RetryingRunnable.class);
    expect(blockingQueue.poll()).andReturn(task);
    taskExecutor.execute(task);
    control.replay();
    replay();
    queueDrainer.run();
  }

  @Test
  public void testEmptyQueue() throws Exception {
    expect(blockingQueue.poll()).andReturn(null);
    control.replay();
    replay();
    queueDrainer.run();
  }
}
