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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Small driver program for manually testing serverset operations.
 * @author Patrick Chan
 */
public class ServerSet2Main {
  private static final int SERVERSET_PORT = 2181;
  private static final int MAX_SHARDS = 10;
  private static final int MAX_SLEEP_MS = 20000;

  /**
   * Main entry.
   */
  public static void main(String[] args) {
    try {
      new ServerSet2Main().main();
    } catch (Throwable e) {
      e.printStackTrace();
      Runtime.getRuntime().halt(1);
    }
  }

  /**
   * Main entry.
   */
  public void main() throws Exception {
    ServerSet ss = null;

    Map<String, String> props = new HashMap<String, String>();
    props.put("shard-id", "" + (int) (Math.random() * MAX_SHARDS));
    props.put("time", "" + new Date());

    Server server = null;
    System.out.println("Commands: ");
    System.out.println("  j <port> - join");
    System.out.println("  u - unjoin");
    System.out.println("  l - set listener");
    System.out.println("  c <path> - create serverset");
    System.out.println("  r <port> - random join and unjoin");
    System.out.println("  x - exit");
    while (true) {
      String[] cmd = stdin().split(" ");
      if ("j".equals(cmd[0])) {
        server = new Server(Integer.parseInt(cmd[1]), props);
        ss.join(server);
      } else if ("u".equals(cmd[0])) {
        ss.unjoin(server);
      } else if ("l".equals(cmd[0])) {
        ss.setListener(new Listener());
      } else if ("c".equals(cmd[0])) {
        ss = new ZkServerSet("localhost", SERVERSET_PORT, cmd[1]);
      } else if ("r".equals(cmd[0])) {
        server = new Server(Integer.parseInt(cmd[1]), props);
        while (true) {
          System .out.println("----------- join " + server);
          ss.join(server);
          Thread.sleep((int) (Math.random() * MAX_SLEEP_MS));

          System .out.println("----------- unjoin " + server);
          ss.unjoin(server);
          Thread.sleep((int) (Math.random() * MAX_SLEEP_MS));
        }
      } else if ("x".equals(cmd[0])) {
        throw new IllegalStateException("exit");
      }
    }
  }

  /**
   * Listener of connectivity changes.
   */
  static class Listener implements ServerSetListener {
    public void onChange(Set<Server> serverSet) {
      System.out.println(">> onChange: " + serverSet);
    }

    public void onConnect(boolean connected) {
      System.out.println(">> onConnect: " + connected);
    }
  }

  /**
   * Returns a line read from stdin.
   */
  static String stdin() throws Exception {
    BufferedReader rd = new BufferedReader(new InputStreamReader(System.in));
    return rd.readLine().trim();
  }
}
