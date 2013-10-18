package com.twitter.common.examples.pingpong_thrift.server;

import org.apache.thrift.TException;

import com.twitter.common.examples.pingpong.PingPong;

public class PingPongHandler implements PingPong.Iface {
  @Override
  public String ping() throws TException {
    return "pong";
  }
}
