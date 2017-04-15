package com.foobar;

import java.io.IOException;
import java.net.URL;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

public final class Example {

  private Example() {
  }

  public static String loadResource() throws IOException {
    URL resource = Resources.getResource(Example.class, "example.txt");
    return Resources.toString(resource, Charsets.UTF_8);
  }

  public static void main(String[] args) throws IOException {
    System.out.println("Resource Content: " + loadResource());
  }
}
