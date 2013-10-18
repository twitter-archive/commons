package com.twitter.common.examples.pingpong_thrift.client;

import java.util.logging.Logger;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import com.twitter.common.application.AbstractApplication;
import com.twitter.common.application.AppLauncher;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.examples.pingpong.PingPong;

public class PingPongClient extends AbstractApplication {
  private static final Logger LOG = Logger.getLogger(PingPongClient.class.getName());

  @CmdLine(name = "thrift_port", help = "Server thrift port number.")
  private static final Arg<Integer> THRIFT_PORT = Arg.create(9090);

  @Override
  public void run() {
    TTransport transport = new TSocket("localhost", THRIFT_PORT.get());
    try {
      transport.open();
    } catch (TTransportException e) {
      throw new RuntimeException(e);
    }
    TProtocol protocol = new TBinaryProtocol(transport);
    PingPong.Client client = new PingPong.Client(protocol);

    try {
      LOG.info("Pinging...");
      LOG.info(client.ping());
    } catch (TException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    AppLauncher.launch(PingPongClient.class, args);
  }
}
