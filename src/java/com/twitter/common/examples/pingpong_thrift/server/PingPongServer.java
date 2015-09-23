package com.twitter.common.examples.pingpong_thrift.server;

import java.util.logging.Logger;

import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportException;

import com.twitter.common.application.AbstractApplication;
import com.twitter.common.application.AppLauncher;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.Range;
import com.twitter.common.examples.pingpong.PingPong;

public class PingPongServer extends AbstractApplication {
  private static final Logger LOG = Logger.getLogger(PingPongServer.class.getName());

  @Range(lower = 0, upper = 65535)
  @CmdLine(name = "thrift_port", help = "Port for thrift to listen on.")
  private static final Arg<Integer> THRIFT_PORT = Arg.create(9090);

  @Override
  public void run() {
    PingPongHandler handler = new PingPongHandler();
    PingPong.Processor processor = new PingPong.Processor(handler);
    TServer server;
    try {
      TServerTransport transport = new TServerSocket(THRIFT_PORT.get());
      server = new TSimpleServer(processor, transport);
    } catch (TTransportException e) {
      throw new RuntimeException(e);
    }
    LOG.info("Starting thrift server.");
    server.serve();
  }

  public static void main(String[] args) {
    AppLauncher.launch(PingPongServer.class, args);
  }
}
