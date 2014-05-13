package com.twitter.common.examples.echo;

import java.io.IOException;

/**
 * This is part of the pants echo example. Please see:
 * src/java/com/twitter/common/examples/echo/README.md
 */
public interface Echoer {
  /** Get the echo string */
  String echo() throws IOException;
}
