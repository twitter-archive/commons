// =================================================================================================
// Copyright 2012 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.zookeeper.guice;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.application.modules.LifecycleModule;
import com.twitter.common.application.modules.LocalServiceRegistry;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotEmpty;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.ServerSet;
import com.twitter.common.zookeeper.ServerSet.EndpointStatus;
import com.twitter.common.zookeeper.ServerSet.UpdateException;
import com.twitter.common.zookeeper.ServerSetImpl;
import com.twitter.common.zookeeper.ZooKeeperClient;
import com.twitter.thrift.Status;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A module that registers all ports in the {@link LocalServiceRegistry} in an {@link ServerSet}.
 *
 * Required bindings:
 * <ul>
 *   <li> {@link ZooKeeperClient}
 *   <li> {@link ShutdownRegistry}
 *   <li> {@link LocalServiceRegistry}
 * </ul>
 * {@link LifecycleModule} must also be included by users so a startup action may be registered.
 *
 * @author William Farner
 */
public class ServerSetModule extends AbstractModule {

  private static final Logger LOG = Logger.getLogger(ServerSetModule.class.getName());

  @NotNull
  @NotEmpty
  @CmdLine(name = "serverset_path", help = "ServerSet registration path")
  protected static final Arg<String> SERVERSET_PATH = Arg.create(null);

  @Override
  protected void configure() {
    requireBinding(ZooKeeperClient.class);
    requireBinding(ShutdownRegistry.class);
    requireBinding(LocalServiceRegistry.class);
    LifecycleModule.bindStartupAction(binder(), ServerSetJoiner.class);
  }

  private static class ServerSetJoiner implements Command {
    private final ZooKeeperClient zkClient;
    private final LocalServiceRegistry serviceRegistry;
    private final ShutdownRegistry shutdownRegistry;

    @Inject
    ServerSetJoiner(
        ZooKeeperClient zkClient,
        LocalServiceRegistry serviceRegistry,
        ShutdownRegistry shutdownRegistry) {
      this.zkClient = checkNotNull(zkClient);
      this.serviceRegistry = checkNotNull(serviceRegistry);
      this.shutdownRegistry = checkNotNull(shutdownRegistry);
    }

    @Override public void execute() throws RuntimeException {
      ServerSet serverSet = new ServerSetImpl(zkClient, SERVERSET_PATH.get());

      Optional<InetSocketAddress> primarySocket = serviceRegistry.getPrimarySocket();
      if (!primarySocket.isPresent()) {
        throw new IllegalStateException("No primary service registered with LocalServiceRegistry.");
      }

      final EndpointStatus status;
      try {
        status = serverSet.join(
            primarySocket.get(),
            serviceRegistry.getAuxiliarySockets(),
            Status.ALIVE);
      } catch (JoinException e) {
        LOG.log(Level.WARNING, "Failed to join ServerSet.", e);
        throw new RuntimeException(e);
      } catch (InterruptedException e) {
        LOG.log(Level.WARNING, "Interrupted while joining ServerSet.", e);
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }

      shutdownRegistry.addAction(new ExceptionalCommand<UpdateException>() {
        @Override public void execute() throws UpdateException {
          LOG.info("Leaving ServerSet.");
          status.update(Status.DEAD);
        }
      });
    }
  }
}
