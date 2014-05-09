// =================================================================================================
// Copyright 2014 Twitter, Inc.
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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.easymock.Capture;
import org.easymock.IAnswer;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.testing.FakeClock;

import static org.easymock.EasyMock.anyBoolean;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.captureLong;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class LowResClockTest {

  /**
   * Token representing a unit of work the ScheduledExecutorService need to execute.
   * The caller can wait for the ScheduledExecutorService thread to complete his task
   * by calling the blocking function {@code waitForCompletion}.
   */
  private class Token {
    public synchronized void waitForCompletion() {
      try {
        this.wait();
      } catch (InterruptedException e) {
        /* ignore */
      }
    }

    public synchronized void notifyCompletion() {
      this.notify();
    }
  }

  /**
   * FakeClock that override the `advance` method, it waits for the remote thread to complete
   * its task.
   */
  private class WaitingFakeClock extends FakeClock {
    final SynchronousQueue<Token> queue;

    public WaitingFakeClock(SynchronousQueue<Token> q) {
      this.queue = q;
    }

    public synchronized void advance(Amount<Long, Time> period) {
      super.advance(period);
      Token token = new Token();
      queue.offer(token);
      token.waitForCompletion();
    }
  }

  @Test
  public void testLowResClock() {
    final SynchronousQueue<Token> queue = new SynchronousQueue<Token>();
    final WaitingFakeClock clock = new WaitingFakeClock(queue);

    ScheduledExecutorService mockExecutor = createMock(ScheduledExecutorService.class);
    final Capture<Runnable> runnable = new Capture<Runnable>();
    final Capture<Long> period = new Capture<Long>();
    mockExecutor.scheduleWithFixedDelay(capture(runnable), eq(0L), captureLong(period),
      eq(TimeUnit.MILLISECONDS));
    expectLastCall().andAnswer(new IAnswer() {
      public ScheduledFuture<?> answer() {
        final Thread t = new Thread() {
          @Override
          public void run() {
            long t = clock.nowMillis();
            try {
              while (true) {
                Token token = queue.take();
                if (clock.nowMillis() >= t + period.getValue()) {
                  runnable.getValue().run();
                  t = clock.nowMillis();
                }
                token.notifyCompletion();
              }
            } catch (InterruptedException e) {
              /* terminate */
            }
          }
        };
        t.setDaemon(true);
        t.start();
        final ScheduledFuture<?> future = createMock(ScheduledFuture.class);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        expect(future.isCancelled()).andAnswer(new IAnswer<Boolean>() {
          @Override
          public Boolean answer() throws Throwable {
            return stopped.get();
          }
        }).anyTimes();
        expect(future.cancel(anyBoolean())).andAnswer(new IAnswer<Boolean>() {
          @Override
          public Boolean answer() throws Throwable {
            t.interrupt();
            stopped.set(true);
            return true;
          }
        });
        replay(future);
        return future;
      }
    });
    replay(mockExecutor);

    LowResClock lowRes = new LowResClock(Amount.of(1L, Time.SECONDS), mockExecutor, clock);

    long t = lowRes.nowMillis();
    clock.advance(Amount.of(100L, Time.MILLISECONDS));
    assertEquals(t, lowRes.nowMillis());

    clock.advance(Amount.of(900L, Time.MILLISECONDS));
    assertEquals(t + 1000, lowRes.nowMillis());

    clock.advance(Amount.of(100L, Time.MILLISECONDS));
    assertEquals(t + 1000, lowRes.nowMillis());

    lowRes.close();
    try {
      lowRes.nowMillis();
      fail("Closed clock should throw exception!");
    } catch (IllegalStateException e) {
      /* expected */
    }
  }
}
