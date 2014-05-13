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

import org.junit.Test;

import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class InetSocketAddressHelperTest {

  @Test
  public void testParseValueInvalid() {
    try {
      InetSocketAddressHelper.parse(null);
      fail();
    } catch (NullPointerException e) {
      // expected
    }

    try {
      InetSocketAddressHelper.parse("");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      InetSocketAddressHelper.parse(":");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      InetSocketAddressHelper.parse("*:");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      InetSocketAddressHelper.parse(":jake");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      InetSocketAddressHelper.parse(":70000");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      InetSocketAddressHelper.parse("localhost:");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void testParseArgValuePort() {
    assertEquals(new InetSocketAddress(6666), InetSocketAddressHelper.parse(":6666"));
    assertEquals(new InetSocketAddress(0), InetSocketAddressHelper.parse(":*"));
  }

  @Test
  public void testParseArgValueHostPort() {
    assertEquals(InetSocketAddress.createUnresolved("localhost", 5555),
        InetSocketAddressHelper.parse("localhost:5555"));

    assertEquals(InetSocketAddress.createUnresolved("127.0.0.1", 4444),
        InetSocketAddressHelper.parse("127.0.0.1:4444"));
  }

  @Test
  public void testInetSocketAddressToServerString() {
    assertEquals("localhost:8000",
        InetSocketAddressHelper.toString(InetSocketAddress.createUnresolved("localhost", 8000)));

    assertEquals("foo.bar.baz:8000",
        InetSocketAddressHelper.toString(InetSocketAddress.createUnresolved("foo.bar.baz", 8000)));

    assertEquals("127.0.0.1:8000",
        InetSocketAddressHelper.toString(InetSocketAddress.createUnresolved("127.0.0.1", 8000)));

    assertEquals("10.0.0.1:8000",
        InetSocketAddressHelper.toString(InetSocketAddress.createUnresolved("10.0.0.1", 8000)));

    assertEquals("0.0.0.0:80", InetSocketAddressHelper.toString(new InetSocketAddress(80)));
  }
}
