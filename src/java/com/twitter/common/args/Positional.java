package com.twitter.common.args;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to mark an {@link Arg} for gathering the positional command line arguments.
 */
@Retention(RUNTIME)
@Target({FIELD})
public @interface Positional {
  /**
   * The help string to display on the command line in a usage message.
   *
   * @return Help string.
   */
  String help();

  /**
   * The parser class to use for parsing the positional arguments.  The parser must return the same
   * type as the field being annotated.
   *
   * @return Custom parser for this type.
   */
  // The default is fully qualified to work around an apt bug:
  // http://bugs.sun.com/view_bug.do?bug_id=6512707
  Class<? extends Parser> parser() default com.twitter.common.args.Parser.class;
}
