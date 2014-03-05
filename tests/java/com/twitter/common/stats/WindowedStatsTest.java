package com.twitter.common.stats;

import org.junit.Test;

import com.twitter.common.junit.annotations.TestParallel;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.WindowedStatistics;
import com.twitter.common.util.testing.FakeClock;

import static org.junit.Assert.assertEquals;

@TestParallel
public class WindowedStatsTest {
  private Amount<Long, Time> window = Amount.of(1L, Time.MINUTES);
  private int slices = 3;
  private long sliceDuration = window.as(Time.NANOSECONDS) / slices;

  @Test
  public void testEmptyStats() {
    FakeClock clock = new FakeClock();
    WindowedStatistics ws = new WindowedStatistics(window, slices, clock);

    assertEmpty(ws);
  }

  @Test
  public void testWindowStats() {
    FakeClock clock = new FakeClock();
    WindowedStatistics ws = new WindowedStatistics(window, slices, clock);
    ws.accumulate(1L);
    assertEmpty(ws);

    clock.advance(Amount.of(1 + sliceDuration, Time.NANOSECONDS));
    assertEquals(1L, ws.max());
    assertEquals(1L, ws.min());
    assertEquals(1L, ws.populationSize());
    assertEquals(1L, ws.sum());
    assertEquals(1.0, ws.mean(), 0.01);
    assertEquals(0.0, ws.standardDeviation(), 0.01);

    clock.advance(Amount.of(slices * sliceDuration, Time.NANOSECONDS));
    assertEmpty(ws);
  }

  @Test
  public void testCleaningOfExpiredWindows() {
    FakeClock clock = new FakeClock();
    WindowedStatistics ws = new WindowedStatistics(window, slices, clock);

    long n = 1000L;
    for (int i=0; i<n; i++) {
      ws.accumulate(i);
    }
    assertEmpty(ws);

    clock.advance(Amount.of(1 + sliceDuration, Time.NANOSECONDS));
    assertEquals(n, ws.populationSize()); // this window is not empty

    clock.advance(Amount.of(100 * sliceDuration, Time.NANOSECONDS));
    assertEmpty(ws); // this window has been cleaned
  }

  @Test
  public void testAddNewValueToFullWS() {
    FakeClock clock = new FakeClock();
    WindowedStatistics ws = new WindowedStatistics(window, slices, clock);

    // AAAAAAAAAAAAAAA
    //      BBBBBBBBBBBBBBB
    //           CCCCCCCCCCCCCCC
    //                DDDDDDDDDDDDDDD
    //                |    |    |    |
    //---------------------------------> t
    //                t=0  t=1  t=2  t=3

    // t=0 fill {B,C,D}
    long n = 1000L;
    for (int i=0; i<n; i++) {
      ws.accumulate(i);
    }
    // read {A}, which should be empty
    assertEmpty(ws);

    clock.advance(Amount.of(1 + sliceDuration, Time.NANOSECONDS));
    // t=1, read {B} which shouldn't be empty

    assertEquals(n - 1L, ws.max());
    assertEquals(0L, ws.min());
    assertEquals(n, ws.populationSize());
    assertEquals(n * (n - 1) / 2, ws.sum());
    assertEquals((n - 1) / 2.0, ws.mean(), 0.01);

    clock.advance(Amount.of(1 + sliceDuration, Time.NANOSECONDS));
    // t=2, read {C} which shouldn't be empty as well

    assertEquals(n - 1L, ws.max());
    assertEquals(0L, ws.min());
    assertEquals(n, ws.populationSize());
    assertEquals(n * (n - 1) / 2, ws.sum());
    assertEquals((n - 1) / 2.0, ws.mean(), 0.01);

    clock.advance(Amount.of(1 + sliceDuration, Time.NANOSECONDS));
    // t=3, read {D} which shouldn't be empty as well

    assertEquals(n - 1L, ws.max());
    assertEquals(0L, ws.min());
    assertEquals(n, ws.populationSize());
    assertEquals(n * (n - 1) / 2, ws.sum());
    assertEquals((n - 1) / 2.0, ws.mean(), 0.01);

    clock.advance(Amount.of(1 + sliceDuration, Time.NANOSECONDS));

    // t=4, read {A} which must be empty (cleaned by the Windowed class)
    assertEmpty(ws);
  }

  private void assertEmpty(WindowedStatistics ws) {
    assertEquals(Long.MIN_VALUE, ws.max());
    assertEquals(Long.MAX_VALUE, ws.min());
    assertEquals(0L, ws.populationSize());
    assertEquals(0L, ws.sum());
    assertEquals(0.0, ws.mean(), 0.01);
    assertEquals(Double.NaN, ws.standardDeviation(), 0.01);
  }
}
