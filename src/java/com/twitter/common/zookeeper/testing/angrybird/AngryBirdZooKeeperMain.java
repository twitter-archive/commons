package com.twitter.common.zookeeper.testing.angrybird;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnegative;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;

import org.apache.thrift.protocol.TBinaryProtocol;

import com.twitter.common.application.AbstractApplication;
import com.twitter.common.application.AppLauncher;
import com.twitter.common.application.Lifecycle;
import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.application.modules.LifecycleModule;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.args.constraints.Positive;
import com.twitter.common.base.Command;
import com.twitter.finagle.builder.Server;
import com.twitter.finagle.builder.ServerBuilder;
import com.twitter.finagle.thrift.ThriftServerFramedCodec;
import com.twitter.common.zookeeper.testing.angrybird.gen.ZooKeeperThriftServer;
import com.twitter.util.Duration;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Angrybird ZooKeeper server launcher.
 */
public class AngryBirdZooKeeperMain extends AbstractApplication {

  private static final Logger LOG = Logger.getLogger(AngryBirdZooKeeperMain.class.getName());

  @NotNull
  @Positive
  @CmdLine(name = "thrift_port", help = "Thrift server port.")
  private static final Arg<Integer> THRIFT_PORT = Arg.create();

  @Nonnegative
  @CmdLine(name = "zk_port", help = "Zookeeper server port")
  private static final Arg<Integer> ZK_PORT = Arg.create(0);

  @Inject private Lifecycle lifecycle;

  private static class ThriftServiceStarter implements Command {
    private final ShutdownRegistry shutdownRegistry;

    @Inject ThriftServiceStarter(ShutdownRegistry shutdownRegistry) {
      this.shutdownRegistry = checkNotNull(shutdownRegistry);
    }

    @Override public void execute() {
      AngryBirdZooKeeperServer zooKeeperServer;

      try {
        zooKeeperServer = new AngryBirdZooKeeperServer(ZK_PORT.get(), shutdownRegistry);
        int port = zooKeeperServer.startNetwork();
        LOG.info(String.format("ZooKeeper server started on: %d", port));
      } catch (IOException e) {
        throw new RuntimeException("Failed to start server: " + e);
      } catch (InterruptedException e) {
        throw new RuntimeException("Failed to start server: " + e);
      }

      final Server thriftServer = ServerBuilder.safeBuild(
          new ZooKeeperThriftServer.Service(new AngryBirdZooKeeperThriftService(zooKeeperServer),
              new TBinaryProtocol.Factory()),
          ServerBuilder.get()
              .name("AngryBirdZooKeeperServer")
              .codec(ThriftServerFramedCodec.get())
              .bindTo(new InetSocketAddress(THRIFT_PORT.get())));

      shutdownRegistry.addAction(new Command() {
        @Override public void execute() {
          thriftServer.close(Duration.forever());
        }
      });
    }
  }

  private static class AngryBirdZooKeeperModule extends AbstractModule {
    @Override
    public void configure() {
      LifecycleModule.bindStartupAction(binder(), ThriftServiceStarter.class);
    }
  }

  @Override
  public void run() {
    LOG.info("Starting AngryBird ZooKeeper Server");
    lifecycle.awaitShutdown();
  }

  @Override
  public Iterable<? extends Module> getModules() {
    return Arrays.asList(new AngryBirdZooKeeperModule());
  }

  public static void main(String[] args) {
    // Enable logging for apache zk.
    org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.INFO);
    org.apache.log4j.BasicConfigurator.configure();

    AppLauncher.launch(AngryBirdZooKeeperMain.class, args);
  }
}
