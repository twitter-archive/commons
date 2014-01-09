package com.twitter.common.security.unittest;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.Permission;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.net.InetAddresses;

/**
 * A {@link SecurityManager} designed to provide secure environment for unit tests.
 */
public class UnitTestSecurityManager extends SecurityManager {
  private static final String LOCALHOST = "localhost";

  private static String getLocalHostName() {
    try {
      return InetAddress.getLocalHost().getHostName().toLowerCase();
    } catch (UnknownHostException e) {
      return ""; // this node doesn't not have name
    }
  }

  private static Set<InetAddress> getMyAddresses() {
    try {
      return ImmutableSet.copyOf(Iterators.concat(Iterators.transform(
          Iterators.forEnumeration(NetworkInterface.getNetworkInterfaces()),
          new Function<NetworkInterface, Iterator<InetAddress>>() {
            @Override public Iterator<InetAddress> apply(NetworkInterface iface) {
              return Iterators.forEnumeration(iface.getInetAddresses());
            }
          })));
    } catch (SocketException e) {
      return Collections.emptySet();
    }
  }

  private final String myName;
  private final Set<InetAddress> myAddresses;

  /**
   * To construct this class, caller needs to have NetPermission("getNetworkInformation")
   */
  public UnitTestSecurityManager() {
    this(getLocalHostName(), getMyAddresses());
  }

  @VisibleForTesting
  UnitTestSecurityManager(String name, Set<InetAddress> addresses) {
    myName = name;
    myAddresses = addresses;
  }

  @Override
  public void checkConnect(String host, int port) {
    validateHost(host);
  }

  @Override
  public void checkConnect(String host, int port, Object context) {
    validateHost(host);
  }


  @Override
  public void checkPermission(Permission perm) {
    // no-op; permit any action
  }

  @Override
  public void checkPermission(Permission perm, Object context) {
    // no-op; permit any action
  }

  /**
   * Check if:
   * <ul>
   *   <li>host is "localhost" or this machine name, or
   *   <li>host is valid IP address (not hostname), and
   *   <ul>
   *    <li>loopback address, or
   *    <li>one of the addresses assigned to this host.
   *   </ul>
   * </ul>
   *
   * throw {@link SecurityException} if not.
   */
  private void validateHost(String host) {
    if (LOCALHOST.equalsIgnoreCase(host) || myName.equalsIgnoreCase(host)) {
      return;
    }

    String message = String.format("Connecting to %s is blocked by %s.",
        host, this.getClass().getSimpleName());

    // check if "host" represents IP address, not a machine name which is handled above.
    if (!InetAddresses.isInetAddress(host)) {
      throw new SecurityException(message);
    }

    InetAddress addr = InetAddresses.forString(host);

    if (addr.isAnyLocalAddress()
        || addr.isLoopbackAddress()
        || myAddresses.contains(addr)) {
      return;
    }

    throw new SecurityException(message);
  }
}
