package com.twitter.common.stats;

import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.twitter.common.base.MorePreconditions;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

/**
 * A stat that exports the amount of time since it was last reset.
 *
 * @author William Farner
 */
public class Elapsed {

  private final Clock clock;
  private final AtomicLong lastEventNs = new AtomicLong();

  /**
   * Calls {@link #Elapsed(String, Time)} using a default granularity of nanoseconds.
   *
   * @param name Name of the stat to export.
   */
  public Elapsed(String name) {
    this(name, Time.NANOSECONDS);
  }

  /**
   * Creates and exports a new stat that maintains the difference between the system clock time
   * and the time since it was last reset.  Upon export, the counter will act as though it were just
   * reset.
   *
   * @param name Name of the stat to export.
   * @param granularity Time unit granularity to export.
   */
  public Elapsed(String name, Time granularity) {
    this(name, granularity, Clock.SYSTEM_CLOCK);
  }

  @VisibleForTesting
  Elapsed(String name, final Time granularity, final Clock clock) {
    MorePreconditions.checkNotBlank(name);
    Preconditions.checkNotNull(granularity);
    this.clock = Preconditions.checkNotNull(clock);

    reset();

    Stats.export(new StatImpl<Long>(name) {
      @Override public Long read() {
        return Amount.of(clock.nowNanos() - lastEventNs.get(), Time.NANOSECONDS).as(granularity);
      }
    });
  }

  public void reset() {
    lastEventNs.set(clock.nowNanos());
  }
}
