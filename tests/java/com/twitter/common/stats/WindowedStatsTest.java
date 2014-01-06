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
  private int slices = 6;
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

  private void assertEmpty(WindowedStatistics ws) {
    assertEquals(Long.MIN_VALUE, ws.max());
    assertEquals(Long.MAX_VALUE, ws.min());
    assertEquals(0L, ws.populationSize());
    assertEquals(0L, ws.sum());
    assertEquals(0.0, ws.mean(), 0.01);
    assertEquals(Double.NaN, ws.standardDeviation(), 0.01);
  }
}
