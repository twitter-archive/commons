package com.twitter.common.application;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.net.InetSocketAddressHelper;

import static org.junit.Assert.assertEquals;

/**
 * @author William Farner
 */
public class LocalServiceRegistryTest {

  private static final String A = "a";
  private static final String B = "b";
  private static final String C = "c";

  private static final Set<String> PORTS = ImmutableSet.of(A, B, C);

  private LocalServiceRegistry registry;

  @Before
  public void setUp() {
    registry = new LocalServiceRegistry(PORTS);
  }

  @Test
  public void testAllRegistered() throws Exception {
    registry.announce(A, 1, true);
    registry.announce(B, 2, false);
    registry.announce(C, 3, false);

    checkPorts(A, ImmutableMap.of(
        A, 1,
        B, 2,
        C, 3));
  }

  @Test
  public void testAllowsPortReuse() throws Exception {
    registry.announce(A, 1, true);
    registry.announce(B, 2, false);
    registry.announce(C, 2, false);

    checkPorts(A, ImmutableMap.of(
        A, 1,
        B, 2,
        C, 2));
  }

  @Test(expected = IllegalStateException.class)
  public void testGetPrimaryBeforeAllAnnounced() {
    registry.announce(A, 1, true);
    registry.announce(B, 2, false);

    registry.getPrimaryPort();
  }

  @Test(expected = IllegalStateException.class)
  public void testGetAnnoucedBeforeAllAnnounced() {
    registry.announce(A, 1, true);
    registry.announce(B, 2, false);

    registry.getAllAnnouncedPorts();
  }

  @Test(expected = IllegalStateException.class)
  public void testGetAuxBeforeAllAnnounced() {
    registry.announce(A, 1, false);
    registry.announce(B, 2, false);

    registry.getAuxiliarySockets();
  }

  @Test(expected = IllegalStateException.class)
  public void testDoubleAnnounce() {
    registry.announce(A, 1, false);
    registry.announce(A, 2, false);
  }

  @Test(expected = IllegalStateException.class)
  public void testNoPrimary() {
    registry.announce(A, 1, false);
    registry.announce(B, 2, false);
    registry.announce(C, 3, false);

    registry.getPrimarySocket();
  }

  @Test(expected = IllegalStateException.class)
  public void testMultiplePrimaries() {
    registry.announce(A, 1, true);
    registry.announce(B, 2, true);
  }

  private void checkPorts(@Nullable String primary, Map<String, Integer> expected)
      throws Exception {
    assertEquals(primary, registry.getPrimaryPort());
    assertEquals(expected, registry.getAllAnnouncedPorts());

    Map<String, Integer> auxPorts = Maps.newHashMap(expected);
    if (primary != null) {
      assertEquals(InetSocketAddressHelper.getLocalAddress(expected.get(primary)),
          registry.getPrimarySocket());
      auxPorts.remove(primary);
    }

    assertEquals(Maps.transformValues(auxPorts, InetSocketAddressHelper.INT_TO_INET),
        registry.getAuxiliarySockets());
  }
}
