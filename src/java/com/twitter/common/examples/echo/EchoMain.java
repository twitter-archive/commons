package com.twitter.common.examples.echo;

/**
 * This is part of the pants echo example. Please see:
 * src/java/com/twitter/common/examples/echo/README.md
 */
public final class EchoMain {

  private EchoMain() {
  }

  /**
   * First arg is {@link Echoer} implementation to use.
   */
  public static void main(String[] args) throws Exception {
    System.out.println("Using Echoer: " + args[0]);
    Echoer echoer = (Echoer) Class.forName(args[0]).newInstance();
    System.out.println(echoer.echo());
  }
}
