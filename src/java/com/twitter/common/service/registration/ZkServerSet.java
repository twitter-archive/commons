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

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;

import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.BackoffHelper;
import com.twitter.common.zookeeper.Group;
import com.twitter.common.zookeeper.Group.GroupChangeListener;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.ZooKeeperClient;

/**
 * This ServerSet implementation uses ZooKeeper to implement membership.
 * @author Patrick Chan
 */
public class ZkServerSet implements ServerSet {
  private static final Logger LOG = Logger.getLogger(ZkServerSet.class.getName());
  private static final int MAX_SOCKET_NUMBER = 0xFFFF;
  private static final int ZK_TIMEOUT = 10;

  private ZooKeeperClient zkClient;

  // Input
  private final String host;
  private final int port;
  private final String path;

  // Background threads
  private ServerSetListener listener;
  private JoinThread joinThread;
  private ListenerThread listenerThread;
  private final BackoffHelper backoffHelper = new BackoffHelper();

  // Current state
  private Map<String, Server> servers = new HashMap<String, Server>();
  private boolean isConnected = false;

  // ZooKeeper
  private Group group;
  private Group.Membership membership;
  private com.twitter.common.zookeeper.ServerSet sset;

  /**
   * Constructs a server set based on the supplied parameters.
   * The returned object can be used to immediately join the server set
   * (see {@link join()} and/or to discover the existing members in the server set
   * (see {@link setListener()}).
   *
   * The supplied names must consist only of alphanumerics; no punctuation or white
   * space is allowed.
   *
   * This constructor tries to catch and handle all connection-related exceptions.
   * Any runtime exception that is thrown indicates a permanent configuration or programming error.
   *
   * @param host        non-null host name or IP for a zookeeper server
   * @param port        the port of the zookeeper server
   * @param path        non-null zookeeper path
   */
  public ZkServerSet(String host, int port, String path) {
    Preconditions.checkNotNull(host);
    Preconditions.checkArgument(port > 0 && port < MAX_SOCKET_NUMBER,
                                "port must be > 0 and < 0xFFFF");
    Preconditions.checkNotNull(path);

    this.host = host;
    this.port = port;
    this.path = path;

    // If the server doesn't hear from the client by the timeout
    // any empheral nodes created by the client will be removed.
    zkClient = new ZooKeeperClient(Amount.of(ZK_TIMEOUT, Time.SECONDS),
        ImmutableList.of(InetSocketAddress.createUnresolved(host, port)));
    zkClient.register(new MyWatcher());
    group = new Group(zkClient, ZooDefs.Ids.OPEN_ACL_UNSAFE, path, "server_");
    sset = new com.twitter.common.zookeeper.ServerSetImpl(zkClient, group);
  }

  /**
   * Watcher for zookeeper events.
   */
  class MyWatcher implements Watcher {
    @Override public void process(WatchedEvent event) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.log(Level.FINE, "" + event);
      }
      if (event.getType() == Watcher.Event.EventType.None) {
        switch (event.getState()) {
          case Disconnected:
            if (listener != null) {
              listener.onConnect(false);
            }
            break;
          case SyncConnected:
            if (listener != null) {
              listener.onConnect(true);
            }
            break;
          case Expired:         // Do nothing since zkClient will reestablish connection
          case NoSyncConnected: // Deprecated
          case Unknown:
          default:
            break;
        }
      }
    }
  };


  /**
   * See {@link ServerSet.join()} for details.
   */
  public void join(Server server) {
    if (joinThread != null) {
      throw new IllegalStateException("join() cannot be called again until unjoin() is called.");
    }
    joinThread = new JoinThread(server);
    joinThread.setDaemon(true);
    joinThread.start();
  }

  /**
   * This thread keeps trying to join the group until the join is successful.
   * Once joined, it waits around until unjoin() is called.
   * Note: it might be better to have the thread exit after the join succeeds.
   * Just need to handle the race condition of an unjoin() call just as the
   * join succeeds. Need to make sure membership.cancel() is called if the
   * membership object is successfully created.
   */
  class JoinThread extends Thread {
    private Server server;
    private boolean cancelled = false;

    JoinThread(Server server) {
      this.server = server;
    }

    public synchronized void cancel() {
      cancelled = true;
      notify();
    }

    public void run() {
      try {
        // Prepare the serialized data
        final Supplier<byte[]> data = new Supplier<byte[]>() {
          @Override public byte[] get() {
            return server.getSerialized();
          }
        };

        // Continue call join() until it succeeds
        backoffHelper.doUntilSuccess(new ExceptionalSupplier<Boolean, RuntimeException>() {
          @Override public Boolean get() {
            try {
              if (!cancelled) {
                membership = group.join(data);
              }
              return true;
            } catch (Exception e) {
              LOG.log(Level.WARNING, "Join thread failed to join as " + server
                      + ". Will retry.", e);
            }
            return false;
          }
        });

        // Join succeeded. Now wait for cancelled.
        synchronized (this) {
          while (!cancelled) {
            wait();
          }
        }

        // unjoin() was called so cancel the membership
        // TODO: If an exception occurs, do we need to try again?
        if (membership != null) {
          try {
            membership.cancel();
          } catch (JoinException e) {
            LOG.log(Level.WARNING, "Join thread cancel() failed for '" + server + "'. Ignored.", e);
          }
        }
      } catch (InterruptedException e) {
        LOG.log(Level.WARNING, "Join thread was interrupted, which should never happen.", e);
      }
    }
  }

  /**
   * See {@link ServerSet.unjoin()} for details.
   */
  public void unjoin(Server server) {
    if (joinThread == null) {
      throw new IllegalStateException("join() has not yet been called.");
    }
    if (!joinThread.server.equals(server)) {
      throw new IllegalStateException(
        "The supplied server object does not equal to the object supplied to join().");
    }
    if (joinThread != null) {
      joinThread.cancel();
      joinThread = null;
    }
  }

  /**
   * See {@link ServerSet.setListener()} for details.
   */
  public void setListener(ServerSetListener listener) {
    if (this.listener != null) {
      throw new IllegalStateException("setListener() cannot be called again.");
    }
    this.listener = listener;

    // Initialize connection state.
    // There's a race condition that exists between the watcher and this thread
    // which can cause the listener to be left with incorrect state.
    // E.g. this thread sees that isConnected is false and will invoke
    // the callback with false. Before the callback is called, the watcher sets
    // isConnected to true. When this thread finally gets around to invoking the callback,
    // it does so with old state.
    // To fix the race condition, a check is made before and after the callback.
    // If it's different, the callback is invoked again.
    // Although the callback might be invoked with the same value, the state
    // will eventually be consistent.
    boolean b = false;
    do {
      b = isConnected;
      listener.onConnect(b);
    } while (b != isConnected);

    listenerThread = new ListenerThread();
    listenerThread.setDaemon(true);
    listenerThread.start();
  }

  /**
   * This thread continues to try and set a watch. Once the watch is set, this thread
   * terminates.
   */
  class ListenerThread extends Thread {
    public void run() {
      try {
        // Set a watch to look for group changes.
        // Continues to call watch() until it succeeds.
        backoffHelper.doUntilSuccess(new ExceptionalSupplier<Boolean, RuntimeException>() {
          @Override public Boolean get() {
            try {
              // This method blocks if no connectivity to ZK.
              // The watch will invoke the callback with the initial state.
              group.watch(new GroupChangeListener() {
                @Override public void onGroupChange(Iterable<String> memberIds) {
                  notifyGroupChange(memberIds);
                }
              });
              return true;
            } catch (Exception e) {
              LOG.log(Level.WARNING, "Join thread failed to set watch. Will retry.", e);
            }
            return false;
          }
        });
      } catch (InterruptedException e) {
        LOG.log(Level.WARNING, "Listener thread was interrupted, which should never happen.", e);
      }
    }
  }

  /**
   * This method takes the list of live znode names and updates the server set's list of servers
   * and invokes the callback if there are any changes.
   * Any errors encountered while trying to retrieve node information for a server is
   * logged and ignored and the server is not included in the set.
   *
   * This method is thread-safe.
   *
   * @param memberIds non-null list of the latest live empheral znode names.
   */
  void notifyGroupChange(Iterable<String> memberIds) {
    // Do all the calculations on a copy
    Map<String, Server> serversCopy = new HashMap<String, Server>(servers);

    // Check if there are any changes
    Set<String> latest = new HashSet<String>();
    for (String m : memberIds) {
      latest.add(m);
    }
    if (latest.equals(serversCopy.keySet())) {
      return;
    }

    // Add new servers
    for (String m : memberIds) {
      if (!serversCopy.containsKey(m)) {
        try {
          // Get the data
          Server server = Server.deserialize(group.getMemberData(m));
          serversCopy.put(m, server);
        } catch (Exception e) {
          LOG.log(Level.WARNING, "Could not deserialize data for server " + m, e);
        }
      }
    }

    // Remove missing servers
    Iterator it = serversCopy.keySet().iterator();
    while (it.hasNext()) {
      if (!latest.contains(it.next())) {
        it.remove();
      }
    }

    // Call the listener with a copy of the server set
    listener.onChange(new HashSet<Server>(serversCopy.values()));

    // Update the current list of servers with the latest information
    servers = serversCopy;
  }

  ZooKeeperClient getZkClient() {
    return zkClient;
  }
}
