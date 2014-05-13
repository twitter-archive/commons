package com.twitter.common.examples.echo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import com.google.common.io.Closer;

/**
 * This is part of the pants echo example. Please see:
 * src/java/com/twitter/common/examples/echo/README.md
 */
public class FileEchoer implements Echoer {
  @Override
  public String echo() throws IOException {
    Closer closer = Closer.create();
    try {
      FileReader fileReader = closer.register(new FileReader("/etc/hosts"));
      BufferedReader bufferedReader = closer.register(new BufferedReader(fileReader));
      return bufferedReader.readLine();
    } finally {
      closer.close();
    }
  }
}
