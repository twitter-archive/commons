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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Atomics;
import com.google.inject.BindingAnnotation;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.application.modules.LifecycleModule;
import com.twitter.common.application.modules.LocalServiceRegistry;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotEmpty;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.base.Supplier;
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
 *
 * {@link LifecycleModule} must also be included by users so a startup action may be registered.
 *
 * Provided bindings:
 * <ul>
 *   <li> {@link Supplier<EndpointStatus>}
 * </ul>
 *
 * @author William Farner
 */
public class ServerSetModule extends AbstractModule {

  /**
   * BindingAnnotation for defaults to use in the service instance node.
   */
  @BindingAnnotation @Target({ PARAMETER, METHOD, FIELD }) @Retention(RUNTIME)
  private @interface Default { }

  @NotNull
  @NotEmpty
  @CmdLine(name = "serverset_path", help = "ServerSet registration path")
  protected static final Arg<String> SERVERSET_PATH = Arg.create(null);

  @CmdLine(name = "aux_port_as_primary",
      help = "Name of the auxiliary port to use as the primary port in the server set."
          + " This may only be used when no other primary port is specified.")
  private static final Arg<String> AUX_PORT_AS_PRIMARY = Arg.create(null);

  private static final Logger LOG = Logger.getLogger(ServerSetModule.class.getName());

  private final Status initialStatus;
  private final Optional<String> auxPortAsPrimary;

  /**
   * Calls {@link #ServerSetModule(Optional)} with an absent value.
   */
  public ServerSetModule() {
    this(Optional.<String>absent());
  }

  /**
   * Calls {@link #ServerSetModule(Status, Optional)} with initial status {@link Status#ALIVE}.
   *
   * @param auxPortAsPrimary Name of the auxiliary port to use as the primary port.
   */
  public ServerSetModule(Optional<String> auxPortAsPrimary) {
    this(Status.ALIVE, auxPortAsPrimary);
  }

  /**
   * Constructs a ServerSetModule that registers a startup action that registers this process in
   * ZooKeeper, with the specified initial Status.
   *
   * @param initialStatus initial Status to report to ZooKeeper.
   */
  public ServerSetModule(Status initialStatus) {
    this(initialStatus, Optional.<String>absent());
  }

  /**
   * Constructs a ServerSetModule that registers a startup action to register this process in
   * ZooKeeper, with the specified initial status and auxiliary port to represent as the primary
   * service port.
   *
   * @param initialStatus initial Status to report to ZooKeeper.
   * @param auxPortAsPrimary Name of the auxiliary port to use as the primary port.
   */
  public ServerSetModule(Status initialStatus, Optional<String> auxPortAsPrimary) {
    this.initialStatus = Preconditions.checkNotNull(initialStatus);
    this.auxPortAsPrimary = Preconditions.checkNotNull(auxPortAsPrimary);
  }

  @Override
  protected void configure() {
    requireBinding(ZooKeeperClient.class);
    requireBinding(ShutdownRegistry.class);
    requireBinding(LocalServiceRegistry.class);
    LifecycleModule.bindStartupAction(binder(), ServerSetJoiner.class);

    bind(new TypeLiteral<Supplier<EndpointStatus>>() { }).to(EndpointSupplier.class);
    bind(EndpointSupplier.class).in(Singleton.class);
    bind(Status.class).annotatedWith(Default.class).toInstance(initialStatus);

    Optional<String> primaryPortName;
    if (AUX_PORT_AS_PRIMARY.hasAppliedValue()) {
      primaryPortName = Optional.of(AUX_PORT_AS_PRIMARY.get());
    } else {
      primaryPortName = auxPortAsPrimary;
    }

    bind(new TypeLiteral<Optional<String>>() { }).annotatedWith(Default.class)
        .toInstance(primaryPortName);
  }

  @Provides
  @Singleton
  ServerSet provideServerSet(ZooKeeperClient zkClient) {
    return new ServerSetImpl(zkClient, SERVERSET_PATH.get());
  }

  static class EndpointSupplier implements Supplier<EndpointStatus> {
    private final AtomicReference<EndpointStatus> reference = Atomics.newReference();

    @Nullable
    @Override public EndpointStatus get() {
      return reference.get();
    }

    void set(EndpointStatus endpoint) {
      reference.set(endpoint);
    }
  }

  private static class ServerSetJoiner implements Command {
    private final ServerSet serverSet;
    private final LocalServiceRegistry serviceRegistry;
    private final ShutdownRegistry shutdownRegistry;
    private final EndpointSupplier endpointSupplier;
    private final Status initialStatus;
    private final Optional<String> auxPortAsPrimary;

    @Inject
    ServerSetJoiner(
        ServerSet serverSet,
        LocalServiceRegistry serviceRegistry,
        ShutdownRegistry shutdownRegistry,
        EndpointSupplier endpointSupplier,
        @Default Status initialStatus,
        @Default Optional<String> auxPortAsPrimary) {

      this.serverSet = checkNotNull(serverSet);
      this.serviceRegistry = checkNotNull(serviceRegistry);
      this.shutdownRegistry = checkNotNull(shutdownRegistry);
      this.endpointSupplier = checkNotNull(endpointSupplier);
      this.initialStatus = checkNotNull(initialStatus);
      this.auxPortAsPrimary = checkNotNull(auxPortAsPrimary);
    }

    @Override public void execute() {
      Optional<InetSocketAddress> primarySocket = serviceRegistry.getPrimarySocket();
      Map<String, InetSocketAddress> auxSockets = serviceRegistry.getAuxiliarySockets();

      InetSocketAddress primary;
      if (primarySocket.isPresent()) {
        primary = primarySocket.get();
      } else if (auxPortAsPrimary.isPresent()) {
        primary = auxSockets.get(auxPortAsPrimary.get());
        if (primary == null) {
          throw new IllegalStateException("No auxiliary port named " + auxPortAsPrimary.get());
        }
      } else {
        throw new IllegalStateException("No primary service registered with LocalServiceRegistry,"
            + " and -aux_port_as_primary was not specified.");
      }

      final EndpointStatus endpointStatus;
      try {
        endpointStatus = serverSet.join(primary, auxSockets, initialStatus);
        endpointSupplier.set(endpointStatus);
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
          endpointStatus.update(Status.DEAD);
        }
      });
    }
  }
}
