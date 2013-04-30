package com.twitter.common.testing.runner;

import org.junit.internal.requests.ClassRequest;

/**
 * A ClassRequest that exposes the wrapped class.
 */
public class AnnotatedClassRequest extends ClassRequest {
  private final Class<?> clazz;

  public AnnotatedClassRequest(Class<?> clazz) {
    super(clazz);
    this.clazz = clazz;
  }

  public Class<?> getClazz() {
    return clazz;
  }
}
