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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.easymock.Capture;
import org.easymock.IAnswer;
import org.junit.Test;

import com.twitter.common.base.Command;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.testing.FakeClock;

import static org.easymock.EasyMock.anyBoolean;
import static org.easymock.EasyMock.anyLong;
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
   * A FakeClock that overrides the {@link FakeClock#advance(Amount) advance} method to allow a
   * co-operating thread to execute a synchronous action via {@link #doOnAdvance(Command)}.
   */
  static class WaitingFakeClock extends FakeClock {
    private final SynchronousQueue<CountDownLatch> signalQueue =
        new SynchronousQueue<CountDownLatch>();

    @Override
    public void advance(Amount<Long, Time> period) {
      super.advance(period);
      CountDownLatch signal = new CountDownLatch(1);
      try {
        signalQueue.put(signal);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      try {
        signal.await();
      } catch (InterruptedException e) {
        // ignore
      }
    }

    void doOnAdvance(Command action) throws InterruptedException {
      CountDownLatch signal = signalQueue.take();
      action.execute();
      signal.countDown();
    }
  }

  static class Tick implements Command {
    private final Clock clock;
    private final long period;
    private final Runnable advancer;
    private long time;

    Tick(Clock clock, long startTime, long period, Runnable advancer) {
      this.clock = clock;
      time = startTime;
      this.period = period;
      this.advancer = advancer;
    }

    @Override
    public void execute() {
      if (clock.nowMillis() >= time + period) {
        advancer.run();
        time = clock.nowMillis();
      }
    }
  }

  @Test
  public void testLowResClock() {
    final WaitingFakeClock clock = new WaitingFakeClock();
    final long start = clock.nowMillis();

    ScheduledExecutorService mockExecutor = createMock(ScheduledExecutorService.class);
    final Capture<Runnable> runnable = new Capture<Runnable>();
    final Capture<Long> period = new Capture<Long>();
    mockExecutor.scheduleAtFixedRate(capture(runnable), anyLong(), captureLong(period),
        eq(TimeUnit.MILLISECONDS));

    expectLastCall().andAnswer(new IAnswer<ScheduledFuture<?>>() {
      public ScheduledFuture<?> answer() {
        final Thread ticker = new Thread() {
          @Override
          public void run() {
            Tick tick = new Tick(clock, start, period.getValue(), runnable.getValue());
            try {
              while (true) {
                clock.doOnAdvance(tick);
              }
            } catch (InterruptedException e) {
              /* terminate */
            }
          }
        };
        ticker.setDaemon(true);
        ticker.start();
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
            ticker.interrupt();
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
