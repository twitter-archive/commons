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

package com.twitter.common.net;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * A utility that can parse [host]:[port] pairs or :[port] designators into instances of
 * {@link java.net.InetSocketAddress}. The literal '*' can be specified for port as an alternative
 * to '0' to indicate any local port.
 *
 * @author John Sirois
 */
public final class InetSocketAddressHelper {

  /**
   * A function that uses {@link #parse(String)} to map an endpoint spec to an
   * {@link InetSocketAddress}.
   */
  public static final Function<String, InetSocketAddress> STR_TO_INET =
      new Function<String, InetSocketAddress>() {
        @Override public InetSocketAddress apply(String value) {
          return parse(value);
        }
      };

  /**
   * A function that uses {@link #getLocalAddress(int)} to map a local port number to an
   * {@link InetSocketAddress}.
   * If an {@link UnknownHostException} is thrown, it will be propagated as a
   * {@link RuntimeException}.
   */
  public static final Function<Integer, InetSocketAddress> INT_TO_INET =
      new Function<Integer, InetSocketAddress>() {
        @Override public InetSocketAddress apply(Integer port) {
          try {
            return getLocalAddress(port);
          } catch (UnknownHostException e) {
            throw Throwables.propagate(e);
          }
        }
      };

  public static final Function<InetSocketAddress, String> INET_TO_STR =
      new Function<InetSocketAddress, String>() {
        @Override public String apply(InetSocketAddress addr) {
          return InetSocketAddressHelper.toString(addr);
        }
      };

  /**
   * Attempts to parse an endpoint spec into an InetSocketAddress.
   *
   * @param value the endpoint spec
   * @return a parsed InetSocketAddress
   * @throws NullPointerException     if {@code value} is {@code null}
   * @throws IllegalArgumentException if {@code value} cannot be parsed
   */
  public static InetSocketAddress parse(String value) {
    Preconditions.checkNotNull(value);

    String[] spec = value.split(":", 2);
    if (spec.length != 2) {
      throw new IllegalArgumentException("Invalid socket address spec: " + value);
    }

    String host = spec[0];
    int port = asPort(spec[1]);

    return StringUtils.isEmpty(host)
        ? new InetSocketAddress(port)
        : InetSocketAddress.createUnresolved(host, port);
  }

  /**
   * Attempts to return a usable String given an InetSocketAddress.
   *
   * @param value the InetSocketAddress.
   * @return the String representation of the InetSocketAddress.
   */
  public static String toString(InetSocketAddress value) {
    Preconditions.checkNotNull(value);
    return value.getHostName() + ":" + value.getPort();
  }

  private static int asPort(String port) {
    if ("*".equals(port)) {
      return 0;
    }
    try {
      return Integer.parseInt(port);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid port: " + port, e);
    }
  }

  public static InetSocketAddress getLocalAddress(int port) throws UnknownHostException {
    String ipAddress = InetAddress.getLocalHost().getHostAddress();
    return new InetSocketAddress(ipAddress, port);
  }

  private InetSocketAddressHelper() {
    // utility
  }

  /**
   * Converts backend definitions (in host:port form) a set of socket addresses.
   *
   * @param backends Backends to convert.
   * @return Sockets representing the provided backends.
   */
  public static Set<InetSocketAddress> convertToSockets(Iterable<String> backends) {
    return Sets.newHashSet(Iterables.transform(backends, STR_TO_INET));
  }
}
