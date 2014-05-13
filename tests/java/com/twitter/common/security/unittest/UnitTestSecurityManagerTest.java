package com.twitter.common.security.unittest;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class UnitTestSecurityManagerTest {
  private static final int HTTP_PORT = 80;
  private static final String MY_NAME = "host.example.com";

  private static InetAddress myAddress;

  @BeforeClass
  public static void setUpClass() {
    try {
      myAddress = InetAddress.getByName("192.168.123.1");
    } catch (UnknownHostException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private UnitTestSecurityManager securityManager;

  @Before
  public void setUp() {
    securityManager = new UnitTestSecurityManager(
        MY_NAME, ImmutableSet.of(myAddress));
  }

  @Test
  public void testLocalhost() throws Exception {
    securityManager.checkConnect("LoCaLhOsT", HTTP_PORT);
  }

  @Test
  public void testLocalhostName() throws Exception {
    securityManager.checkConnect(MY_NAME, HTTP_PORT);
  }

  @Test
  public void testLoopback() throws Exception {
    securityManager.checkConnect("127.0.0.1", HTTP_PORT);
  }

  @Test
  public void testMyAddress() throws Exception {
    securityManager.checkConnect(myAddress.getHostAddress(), HTTP_PORT);
  }

  @Test
  public void testAnyLocalAddress() throws Exception {
    securityManager.checkConnect("0.0.0.0", HTTP_PORT);
  }

  @Test(expected = SecurityException.class)
  public void testTwitterDotCom() throws Exception {
    securityManager.checkConnect("twitter.com", HTTP_PORT);
  }

  @Test(expected = SecurityException.class)
  public void testIPv4() throws Exception {
    securityManager.checkConnect("1.2.3.4", HTTP_PORT);
  }

  @Test(expected = SecurityException.class)
  public void testIPv6() throws Exception {
    securityManager.checkConnect("2001:db8::ff00:42:8329", HTTP_PORT);
  }
}
