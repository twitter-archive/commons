package com.twitter.common.metrics;

/**
 * A partial Gauge implementation.
 *
 * @param <T> Value type.
 */
public abstract class AbstractGauge<T extends Number> implements Gauge<T> {

  private final String name;

  /**
   * Creates an abstract gauge using the provided name.
   *
   * @param name Name of the gauge.
   */
  public AbstractGauge(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }
}
