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

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Atomics;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.application.modules.LifecycleModule;
import com.twitter.common.application.modules.LocalServiceRegistry;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotNegative;
import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.base.Supplier;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.ServerSet;
import com.twitter.common.zookeeper.ServerSet.EndpointStatus;
import com.twitter.common.zookeeper.ServerSet.UpdateException;
import com.twitter.thrift.Status;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A module that registers all ports in the {@link LocalServiceRegistry} in an {@link ServerSet}.
 * <p/>
 * Required bindings:
 * <ul>
 * <li> {@link ServerSet}
 * <li> {@link ShutdownRegistry}
 * <li> {@link LocalServiceRegistry}
 * </ul>
 * <p/>
 * {@link LifecycleModule} must also be included by users so a startup action may be registered.
 * <p/>
 * Provided bindings:
 * <ul>
 * <li> {@link Supplier}<{@link EndpointStatus}>
 * </ul>
 */
public class ServerSetModule extends AbstractModule {

  /**
   * BindingAnnotation for defaults to use in the service instance node.
   */
  @BindingAnnotation @Target({PARAMETER, METHOD, FIELD}) @Retention(RUNTIME)
  private @interface Default {}

  /**
   * Binding annotation to give the ServerSetJoiner a fixed known ServerSet that is appropriate to
   * {@link ServerSet#join} on.
   */
  @BindingAnnotation @Target({METHOD, PARAMETER}) @Retention(RUNTIME)
  private @interface Joinable {}

  private static final Key<ServerSet> JOINABLE_SS = Key.get(ServerSet.class, Joinable.class);

  @CmdLine(name = "aux_port_as_primary",
      help = "Name of the auxiliary port to use as the primary port in the server set."
          + " This may only be used when no other primary port is specified.")
  private static final Arg<String> AUX_PORT_AS_PRIMARY = Arg.create(null);

  @NotNegative
  @CmdLine(name = "shard_id", help = "Shard ID for this application.")
  private static final Arg<Integer> SHARD_ID = Arg.create();

  private static final Logger LOG = Logger.getLogger(ServerSetModule.class.getName());

  /**
   * Builds a Module tht can be used to join a {@link ServerSet} with the ports configured in a
   * {@link LocalServiceRegistry}.
   */
  public static class Builder {
    private Key<ServerSet> key = Key.get(ServerSet.class);
    private Optional<String> auxPortAsPrimary = Optional.absent();

    /**
     * Sets the key of the ServerSet to join.
     *
     * @param key Key of the ServerSet to join.
     * @return This builder for chaining calls.
     */
    public Builder key(Key<ServerSet> key) {
      this.key = key;
      return this;
    }

    /**
     * Allows joining an auxiliary port with the specified {@code name} as the primary port of the
     * ServerSet.
     *
     * @param auxPortName The name of the auxiliary port to join as the primary ServerSet port.
     * @return This builder for chaining calls.
     */
    public Builder namedPrimaryPort(String auxPortName) {
      this.auxPortAsPrimary = Optional.of(auxPortName);
      return this;
    }

    /**
     * Creates a Module that will register a startup action that joins a ServerSet when installed.
     *
     * @return A Module.
     */
    public ServerSetModule build() {
      return new ServerSetModule(key, auxPortAsPrimary);
    }
  }

  /**
   * Creates a builder that can be used to configure and create a ServerSetModule.
   *
   * @return A ServerSetModule builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  private final Key<ServerSet> serverSetKey;
  private final Optional<String> auxPortAsPrimary;

  /**
   * Constructs a ServerSetModule that registers a startup action to register this process in
   * ZooKeeper, with the specified initial status and auxiliary port to represent as the primary
   * service port.
   *
   * @param serverSetKey The key the ServerSet to join is bound under.
   * @param auxPortAsPrimary Name of the auxiliary port to use as the primary port.
   */
  ServerSetModule(Key<ServerSet> serverSetKey, Optional<String> auxPortAsPrimary) {

    this.serverSetKey = checkNotNull(serverSetKey);
    this.auxPortAsPrimary = checkNotNull(auxPortAsPrimary);
  }

  @Override
  protected void configure() {
    requireBinding(serverSetKey);
    requireBinding(ShutdownRegistry.class);
    requireBinding(LocalServiceRegistry.class);

    LifecycleModule.bindStartupAction(binder(), ServerSetJoiner.class);

    bind(new TypeLiteral<Supplier<EndpointStatus>>() { }).to(EndpointSupplier.class);
    bind(EndpointSupplier.class).in(Singleton.class);

    Optional<String> primaryPortName;
    if (AUX_PORT_AS_PRIMARY.hasAppliedValue()) {
      primaryPortName = Optional.of(AUX_PORT_AS_PRIMARY.get());
    } else {
      primaryPortName = auxPortAsPrimary;
    }

    bind(new TypeLiteral<Optional<String>>() { }).annotatedWith(Default.class)
        .toInstance(primaryPortName);

    bind(JOINABLE_SS).to(serverSetKey);
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
    private final Optional<String> auxPortAsPrimary;

    @Inject
    ServerSetJoiner(
        @Joinable ServerSet serverSet,
        LocalServiceRegistry serviceRegistry,
        ShutdownRegistry shutdownRegistry,
        EndpointSupplier endpointSupplier,
        @Default Optional<String> auxPortAsPrimary) {

      this.serverSet = checkNotNull(serverSet);
      this.serviceRegistry = checkNotNull(serviceRegistry);
      this.shutdownRegistry = checkNotNull(shutdownRegistry);
      this.endpointSupplier = checkNotNull(endpointSupplier);
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
        if (SHARD_ID.hasAppliedValue()) {
          endpointStatus = serverSet.join(primary, auxSockets, SHARD_ID.get());
        } else {
          endpointStatus = serverSet.join(primary, auxSockets);
        }

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
          endpointStatus.leave();
        }
      });
    }
  }
}
