package com.twitter.common.args;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Annotation to register a command line argument verifier.
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface VerifierFor {
  /**
   * Returns the annotation that marks a field for verification by the annotated
   * {@link com.twitter.common.args.Verifier} class.
   */
  Class<? extends Annotation> value();
}
