// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.service.registration;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * An object of this class represents a server process that has joined a server set.
 *
 * Two server objects are equal if both the host and port are equal.
 *
 * Serialization Format:
 * The serialized form is a UTF-8 encoded JSON string which of a list of two maps. The first
 * map contains private information pertaining to the server. There are currently two
 * special keywords - port and host. The second map is the properties map supplied to
 * the constructor. Both maps have the type Map<String, String>.
 * Here's an example of a JSON string containing two client supplied properties -
 * time and shard-id:
 *
 * [{"port":"3002","host":"TW-MBP13-PChan.local"},
 *  {"time":"Thu Dec 16 23:29:05 PST 2010","shard-id":"4"}]
 * @author Patrick Chan
 */
public class Server implements Comparable {
  private static final int MAX_SOCKET_NUMBER = 0xFFFF;
  private static final String KEY_HOST = "host";
  private static final String KEY_PORT = "port";

  private final String host;
  private final int port;
  private final Map<String, String> props;
  private final byte[] serialized;

  // Convenience variable (host+":"+port) for comparisons
  private String hostPort;

  /**
   * The properties is an arbitrary set of key/value pairs that will be permanently
   * associated with the server. The props are copied by the constructor.
   *
   * @param port    the primary port for this server.
   * @param props   non-null set of properties.
   */
  public Server(int port, Map<String, String> props) throws Exception {
    this(InetAddress.getLocalHost().getHostName(), port, props);
  }

  private Server(String host, int port, Map<String, String> props) {
    Preconditions.checkNotNull(host);
    Preconditions.checkArgument(port > 0 && port < MAX_SOCKET_NUMBER,
                                "port must be > 0 and < 0xFFFF");
    Preconditions.checkNotNull(props);

    this.port = port;
    this.host = host;
    hostPort = host + ":" + port;

    // Make a copy
    this.props = new HashMap<String, String>(props);

    // Serialize now to catch exceptions early
    serialized = serialize();
  }

  /**
   * Returns the name for the local host.
   * This is the name that will identify this server in the server set.
   */
  public String getHost() {
    return host;
  }

  /**
   * Returns the port supplied to the constructor.
   */
  public int getPort() {
    return port;
  }

  /**
   * Returns a copy of the properties map supplied in the constructor.
   */
  public Map<String, String> getProps() {
    return props;
  }

  /**
   * Two server objects are equal if the host and port are equal.
   */
  public int hashCode() {
    return hostPort.hashCode();
  }

  /**
   * Two server objects are equal if the host and port are equal.
   */
  public boolean equals(Object o) {
    if (o instanceof Server) {
      Server s = (Server) o;
      return s.hostPort.equals(hostPort);
    }
    return false;
  }

  /**
   * Two server objects are equal if the host and port are equal.
   */
  public int compareTo(Object o) {
    Server s = (Server) o;
    return hostPort.compareTo(s.hostPort);
  }

  /**
   * Returns a string representing the information in this Server object.
   */
  public String toString() {
    return hostPort + " " + props;
  }

  static Server deserialize(byte[] ser) {
    Gson gson = new Gson();
    List<Map<String, String>> list = gson.fromJson(new String(ser, Charsets.UTF_8),
        new TypeToken<List<Map<String, String>>>() { } .getType());
    Map<String, String> privateMap = list.get(0);
    Map<String, String> props = list.get(1);
    return new Server(privateMap.get(KEY_HOST),
                      Integer.parseInt(privateMap.get(KEY_PORT)), props);
  }

  byte[] getSerialized() {
    return serialized;
  }

  /**
   * The serialized version of this object is a list of two maps - the first element is
   * a private map and the second element is the props map.
   */
  private byte[] serialize() {
    // Create a private map with private values
    ImmutableMap<String, String> privateMap =
      ImmutableMap.of(KEY_HOST, host,
                      KEY_PORT, "" + port);
    List<Map<String, String>> list = Arrays.asList(privateMap, props);

    Gson gson = new Gson();
    return gson.toJson(list).getBytes(Charsets.UTF_8);
  }
}
