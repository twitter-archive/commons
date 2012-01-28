// =================================================================================================
// Copyright 2011 Twitter, Inc.
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

package com.twitter.common.application;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;

import com.twitter.common.base.MorePreconditions;
import com.twitter.common.net.InetSocketAddressHelper;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Registry for services that should be exported from the application.
 *
 * Example of announcing and registering a port:
 * <pre>
 * class MyLauncher extends RegisteringServiceLauncher<LaunchException> {
 *   static final String NAME = "my_service_name";
 *
 *   public String getPortName() {
 *     return NAME
 *   }
 *
 *   public boolean isPrimaryService() {
 *     return false;
 *   }
 *
 *   public Integer get() {
 *     return launchServiceAndGetPort();
 *   }
 * }
 *
 * class MyServiceModule extends AbstractModule {
 *   public void configure() {
 *     LifeCycleModule.bindServiceLauncher(binder(), MyLauncher.NAME, MyLauncher.class);
 *   }
 * }
 * </pre>
 *
 * @author William Farner
 */
public class LocalServiceRegistry {

  @BindingAnnotation
  @Target({FIELD, PARAMETER}) @Retention(RUNTIME)
  public @interface Port {}

  private final Set<String> registeredNames;
  private final Map<String, Integer> announcedPorts = Maps.newHashMap();
  private String primaryPort = null;

  /**
   * Creates a new local service registry.
   *
   * @param registeredNames Names of ports to register.
   */
  @Inject
  public LocalServiceRegistry(@Port Set<String> registeredNames) {
    this.registeredNames = Preconditions.checkNotNull(registeredNames);
  }

  /**
   * Announces a port.
   * This will fail if the port has already been announced, the port was not registered,
   * or if another port was announced with {@code isPrimary = true}.
   *
   * @param portName Name of the port to announce.
   * @param port Port number.
   * @param isPrimary Whether this port is the primary service endppoint for the application.
   */
  public void announce(String portName, int port, boolean isPrimary) {
    MorePreconditions.checkNotBlank(portName, "Port name must not be blank");
    Preconditions.checkState(!isPrimary || (primaryPort == null),
        String.format("Attempted to announce primary port %s, conflicts with port %s",
            portName, primaryPort));
    Preconditions.checkState(registeredNames.contains(portName),
        "Attempted to announce unregistered port " + portName);

    Integer collision = announcedPorts.put(portName, port);
    Preconditions.checkState(collision == null, "Port was announced twice: " + portName);

    if (isPrimary) {
      primaryPort = portName;
    }
  }

  private void checkFullyAnnounced() {
    Set<String> notAnnouncedPorts = Sets.difference(registeredNames, announcedPorts.keySet());
    Preconditions.checkState(notAnnouncedPorts.isEmpty(),
        "Attempted to get announced ports, before ports were announced: " + notAnnouncedPorts);
  }

  /**
   * Gets all of the announced ports.
   * This will fail if not all of the registered ports have been announced.
   *
   * @return All announced ports.
   */
  public Map<String, Integer> getAllAnnouncedPorts() {
    checkFullyAnnounced();
    return ImmutableMap.copyOf(announcedPorts);
  }

  private final Predicate<String> isPrimaryPort = new Predicate<String>() {
    @Override public boolean apply(String portName) {
      return portName.equals(getPrimaryPort());
    }
  };

  /**
   * Convenience method to return a map identical to {@link #getAllAnnouncedPorts()} with two
   * exceptions:
   * <ul>
   *   <li>The primary port (if included) will be ommitted from the map.
   *   <li>Values are unresolved local sockets rather than just port numbers.
   * </ul>
   *
   * @return Auxiliary port mapping.
   */
  public Map<String, InetSocketAddress> getAuxiliarySockets() {
    return ImmutableMap.copyOf(Maps.transformValues(
        Maps.filterKeys(getAllAnnouncedPorts(), Predicates.not(isPrimaryPort)),
        InetSocketAddressHelper.INT_TO_INET));
  }

  /**
   * Gets the optionally-set primary port name.
   *
   * @return The primary port, or {@code null} if no primary port was announced.
   */
  @Nullable
  public String getPrimaryPort() {
    checkFullyAnnounced();
    return primaryPort;
  }

  /**
   * Gets the primary port number, and returns an unresolved local socket address representing that
   * port.
   *
   * @return Local socket address for the primary port.
   * @throws IllegalStateException If the primary port was not set.
   */
  public InetSocketAddress getPrimarySocket() throws IllegalStateException {
    checkFullyAnnounced();
    String primaryPort = getPrimaryPort();
    Preconditions.checkState(primaryPort != null, "The primary port was not set.");
    try {
      return InetSocketAddressHelper.getLocalAddress(announcedPorts.get(primaryPort));
    } catch (UnknownHostException e) {
      throw Throwables.propagate(e);
    }
  }
}
