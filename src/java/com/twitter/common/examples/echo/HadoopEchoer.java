package com.twitter.common.examples.echo;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import com.google.common.io.Closer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 * This is part of the pants echo example. Please see:
 * src/java/com/twitter/common/examples/echo/README.md
 */
public class HadoopEchoer implements Echoer {

  private static final String FILENAME = "file:///etc/hosts";

  @Override
  public String echo() throws IOException {
    Closer closer = Closer.create();
    try {
      Configuration conf = new Configuration();
      Path p = new Path(FILENAME);

      FileSystem fs = closer.register(p.getFileSystem(conf));
      FSDataInputStream fsDataInputStream = closer.register(fs.open(p));
      InputStreamReader inputStreamReader =
          closer.register(new InputStreamReader(fsDataInputStream));
      BufferedReader bufferedReader = closer.register(new BufferedReader(inputStreamReader));
      ByteArrayOutputStream byteArrayOutputStream = closer.register(new ByteArrayOutputStream());
      String line = bufferedReader.readLine();
      if (line == null) {
        throw new RuntimeException("Failed reading line from " + FILENAME);
      }
      byteArrayOutputStream.write(line.getBytes());
      return byteArrayOutputStream.toString();
    } finally {
      closer.close();
    }
  }
}
