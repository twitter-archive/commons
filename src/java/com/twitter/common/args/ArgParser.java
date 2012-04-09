package com.twitter.common.args;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Annotation to register a command line argument parser globally.
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface ArgParser {
}
