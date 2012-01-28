package com.twitter.common.stats;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.testing.FakeClock;

import static org.junit.Assert.assertEquals;

/**
 * @author William Farner
 */
public class ElapsedTest {

  private static final Amount<Long, Time> ONE_SECOND = Amount.of(1L, Time.SECONDS);

  private static final String NAME = "elapsed";

  private FakeClock clock;

  @Before
  public void setUp() {
    clock = new FakeClock();
    Stats.flush();
  }

  private Elapsed elapsed(Time granularity) {
    return new Elapsed(NAME, granularity, clock);
  }

  @Test
  public void testTimeSince() {
    Elapsed elapsed = elapsed(Time.MILLISECONDS);
    checkValue(0);
    clock.advance(ONE_SECOND);
    checkValue(1000);

    elapsed.reset();
    checkValue(0);

    elapsed.reset();
    clock.advance(ONE_SECOND);
    checkValue(1000);
    clock.advance(ONE_SECOND);
    checkValue(2000);
    clock.advance(ONE_SECOND);
    checkValue(3000);
    clock.advance(ONE_SECOND);
    checkValue(4000);
  }

  @Test
  public void testGranularity() {
    Elapsed elapsed = elapsed(Time.HOURS);
    checkValue(0);
    clock.advance(Amount.of(1L, Time.DAYS));
    checkValue(24);

    elapsed.reset();
    clock.advance(Amount.of(1L, Time.MINUTES));
    checkValue(0);
  }

  private void checkValue(long expected) {
    long actual = (Long) Stats.getVariable(NAME).read();
    assertEquals(expected, actual);
  }
}
