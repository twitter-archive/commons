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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.testing.TearDown;
import com.google.common.testing.junit4.TearDownTestCase;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.twitter.common.testing.junit.rules.Retry;
import org.easymock.Capture;
import org.easymock.IAnswer;
import org.junit.Rule;
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

public class LowResClockTest extends TearDownTestCase {

  /**
   * A FakeClock that overrides the `advance` method to wait for the remote thread to complete
   * its task as well as wrapping a memory barrier around all access to the underlying FakeClock's
   * current time to ensure liveness of reads.
   */
  class WaitingFakeClock extends FakeClock {
    final SynchronousQueue<CountDownLatch> signalQueue;

    public WaitingFakeClock(SynchronousQueue<CountDownLatch> signalQueue) {
      this.signalQueue = signalQueue;
    }

    @Override
    public void advance(Amount<Long, Time> period) {
      synchronized (this) {
        super.advance(period);
      }
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

    @Override
    public synchronized void setNowMillis(long nowMillis) {
      super.setNowMillis(nowMillis);
    }

    @Override
    public synchronized long nowMillis() {
      return super.nowMillis();
    }

    @Override
    public synchronized long nowNanos() {
      return super.nowNanos();
    }

    @Override
    public synchronized void waitFor(long millis) {
      super.waitFor(millis);
    }
  }

  static class ThreadDumper {
    private final Collection<String> lines =
        Collections.synchronizedCollection(new LinkedList<String>());
    private final Collection<Thread> threads =
        Collections.synchronizedCollection(new LinkedHashSet<Thread>());

    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> dumpRun;

    ThreadDumper() {
      scheduler = Executors.newScheduledThreadPool(1,
          new ThreadFactoryBuilder().setNameFormat("Dumper").setDaemon(true).build());
      Runnable dumper = new Runnable() {
        @Override public void run() {
          for (Thread thread : threads) {
            String header = "Dumping " + thread.getName();
            lines.add(Strings.repeat("=", header.length()));
            lines.add(header);
            for (StackTraceElement element : thread.getStackTrace()) {
              lines.add("\t" + element);
            }
          }
        }
      };
      dumpRun = scheduler.scheduleAtFixedRate(dumper, 500L, 500L, TimeUnit.MILLISECONDS);
    }

    private boolean stop() {
      if (!dumpRun.isCancelled()) {
        dumpRun.cancel(true);
        scheduler.shutdownNow();
        return true;
      }
      return false;
    }

    public void discard() {
      stop();
    }

    public void dump() {
      if (stop()) {
        System.out.println(Joiner.on('\n').join(lines));
        System.out.flush();
      }
    }

    public void add(Thread thread) {
      threads.add(thread);
    }
  }

  @Rule
  public Retry.Rule rule = new Retry.Rule();

  @Test(timeout = 5000L)
  @Retry(times = 100000)
  public void testLowResClock() {
    final ThreadDumper threadDumper = new ThreadDumper();
    threadDumper.add(Thread.currentThread());
    addTearDown(new TearDown() {
      @Override public void tearDown() {
        threadDumper.dump();
      }
    });

    final SynchronousQueue<CountDownLatch> queue = new SynchronousQueue<CountDownLatch>();
    final WaitingFakeClock clock = new WaitingFakeClock(queue);
    final long start = clock.nowMillis();

    ScheduledExecutorService mockExecutor = createMock(ScheduledExecutorService.class);
    final Capture<Runnable> runnable = new Capture<Runnable>();
    final Capture<Long> period = new Capture<Long>();
    mockExecutor.scheduleWithFixedDelay(capture(runnable), eq(0L), captureLong(period),
        eq(TimeUnit.MILLISECONDS));
    expectLastCall().andAnswer(new IAnswer<ScheduledFuture<?>>() {
      public ScheduledFuture<?> answer() {
        final Thread t = new Thread("Advancer") {
          @Override public void run() {
            long t = start;
            try {
              while (true) {
                CountDownLatch signal = queue.take();
                if (clock.nowMillis() >= t + period.getValue()) {
                  runnable.getValue().run();
                  t = clock.nowMillis();
                }
                signal.countDown();
              }
            } catch (InterruptedException e) {
              /* terminate */
            }
          }
        };
        t.setDaemon(true);
        t.start();
        threadDumper.add(t);
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

    threadDumper.discard();
  }
}
