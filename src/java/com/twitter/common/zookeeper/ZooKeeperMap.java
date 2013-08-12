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

package com.twitter.common.zookeeper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.Sets;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.util.BackoffHelper;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;

/**
 * A ZooKeeper backed {@link Map}.  Initialized with a node path, this map represents child nodes
 * under that path as keys, with the data in those nodes as values.  This map is readonly from
 * clients of this class, and only can be modified via direct zookeeper operations.
 *
 * Note that instances of this class maintain a zookeeper watch for each zookeeper node under the
 * parent, as well as on the parent itself.  Instances of this class should be created via the
 * {@link #create} factory method.
 *
 * As of ZooKeeper Version 3.1, the maximum allowable size of a data node is 1 MB.  A single
 * client should be able to hold up to maintain several thousand watches, but this depends on rate
 * of data change as well.
 *
 * Talk to your zookeeper cluster administrator if you expect number of map entries times number
 * of live clients to exceed a thousand, as a zookeeper cluster is limited by total number of
 * server-side watches enabled.
 *
 * For an example of a set of tools to maintain one of these maps, please see
 * src/scripts/HenAccess.py in the hen repository.
 *
 * @param <V> the type of values this map stores
 */
public class ZooKeeperMap<V> extends ForwardingMap<String, V> {

  /**
   * An optional listener which can be supplied and triggered when entries in a ZooKeeperMap
   * are added, changed or removed. For a ZooKeeperMap of type <V>, the listener will fire a
   * "nodeChanged" event with the name of the ZNode that changed, and its resulting value as
   * interpreted by the provided deserializer. Removal of child nodes triggers the "nodeRemoved"
   * method indicating the name of the ZNode which is no longer present in the map.
   */
  public interface Listener<V> {

    /**
     * Fired when a node is added to the ZooKeeperMap or changed.
     *
     * @param nodeName indicates the name of the ZNode that was added or changed.
     * @param value is the new value of the node after passing through your supplied deserializer.
     */
    void nodeChanged(String nodeName, V value);

    /**
     * Fired when a node is removed from the ZooKeeperMap.
     *
     * @param nodeName indicates the name of the ZNode that was removed from the ZooKeeperMap.
     */
    void nodeRemoved(String nodeName);
  }

  /**
   * Default deserializer for the constructor if you want to simply store the zookeeper byte[] data
   * in this map.
   */
  public static final Function<byte[], byte[]> BYTE_ARRAY_VALUES = Functions.identity();

  /**
   * A listener that ignores all events.
   */
  public static <T> Listener<T> noopListener() {
    return new Listener<T>() {
      @Override public void nodeChanged(String nodeName, T value) { }
      @Override public void nodeRemoved(String nodeName) { }
    };
  }

  private static final Logger LOG = Logger.getLogger(ZooKeeperMap.class.getName());

  private final ZooKeeperClient zkClient;
  private final String nodePath;
  private final Function<byte[], V> deserializer;

  private final ConcurrentMap<String, V> localMap;
  private final Map<String, V> unmodifiableLocalMap;
  private final BackoffHelper backoffHelper;

  private final Listener<V> mapListener;

  // Whether it's safe to re-establish watches if our zookeeper session has expired.
  private final Object safeToRewatchLock;
  private volatile boolean safeToRewatch;

  /**
   * Returns an initialized ZooKeeperMap.  The given path must exist at the time of
   * creation or a {@link KeeperException} will be thrown.
   *
   * @param zkClient a zookeeper client
   * @param nodePath path to a node whose data will be watched
   * @param deserializer a function that converts byte[] data from a zk node to this map's
   *     value type V
   * @param listener is a Listener which fires when values are added, changed, or removed.
   *
   * @throws InterruptedException if the underlying zookeeper server transaction is interrupted
   * @throws KeeperException.NoNodeException if the given nodePath doesn't exist
   * @throws KeeperException if the server signals an error
   * @throws ZooKeeperConnectionException if there was a problem connecting to the zookeeper
   *     cluster
   */
  public static <V> ZooKeeperMap<V> create(
      ZooKeeperClient zkClient,
      String nodePath,
      Function<byte[], V> deserializer,
      Listener<V> listener)
      throws InterruptedException, KeeperException, ZooKeeperConnectionException {

    ZooKeeperMap<V> zkMap = new ZooKeeperMap<V>(zkClient, nodePath, deserializer, listener);
    zkMap.init();
    return zkMap;
  }


  /**
   * Returns an initialized ZooKeeperMap.  The given path must exist at the time of
   * creation or a {@link KeeperException} will be thrown.
   *
   * @param zkClient a zookeeper client
   * @param nodePath path to a node whose data will be watched
   * @param deserializer a function that converts byte[] data from a zk node to this map's
   *     value type V
   *
   * @throws InterruptedException if the underlying zookeeper server transaction is interrupted
   * @throws KeeperException.NoNodeException if the given nodePath doesn't exist
   * @throws KeeperException if the server signals an error
   * @throws ZooKeeperConnectionException if there was a problem connecting to the zookeeper
   *     cluster
   */
  public static <V> ZooKeeperMap<V> create(
      ZooKeeperClient zkClient,
      String nodePath,
      Function<byte[], V> deserializer)
      throws InterruptedException, KeeperException, ZooKeeperConnectionException {

    return ZooKeeperMap.create(zkClient, nodePath, deserializer, ZooKeeperMap.<V>noopListener());
  }

  /**
   * Initializes a ZooKeeperMap.  The given path must exist at the time of object creation or
   * a {@link KeeperException} will be thrown.
   *
   * Please note that this object will not track any remote zookeeper data until {@link #init()}
   * is successfully called.  After construction and before that call, this {@link Map} will
   * be empty.
   *
   * @param zkClient a zookeeper client
   * @param nodePath top-level node path under which the map data lives
   * @param deserializer a function that converts byte[] data from a zk node to this map's
   *     value type V
   * @param mapListener is a Listener which fires when values are added, changed, or removed.
   *
   * @throws InterruptedException if the underlying zookeeper server transaction is interrupted
   * @throws KeeperException.NoNodeException if the given nodePath doesn't exist
   * @throws KeeperException if the server signals an error
   * @throws ZooKeeperConnectionException if there was a problem connecting to the zookeeper
   *     cluster
   */
  @VisibleForTesting
  ZooKeeperMap(
      ZooKeeperClient zkClient,
      String nodePath,
      Function<byte[], V> deserializer,
      Listener<V> mapListener)
      throws InterruptedException, KeeperException, ZooKeeperConnectionException {

    super();

    this.mapListener = Preconditions.checkNotNull(mapListener);
    this.zkClient = Preconditions.checkNotNull(zkClient);
    this.nodePath = MorePreconditions.checkNotBlank(nodePath);
    this.deserializer = Preconditions.checkNotNull(deserializer);

    localMap = new ConcurrentHashMap<String, V>();
    unmodifiableLocalMap = Collections.unmodifiableMap(localMap);
    backoffHelper = new BackoffHelper();
    safeToRewatchLock = new Object();
    safeToRewatch = false;

    if (zkClient.get().exists(nodePath, null) == null) {
      throw new KeeperException.NoNodeException();
    }
  }

  /**
   * Initialize zookeeper tracking for this {@link Map}.  Once this call returns, this object
   * will be tracking data in zookeeper.
   *
   * @throws InterruptedException if the underlying zookeeper server transaction is interrupted
   * @throws KeeperException if the server signals an error
   * @throws ZooKeeperConnectionException if there was a problem connecting to the zookeeper
   *     cluster
   */
  @VisibleForTesting
  void init() throws InterruptedException, KeeperException, ZooKeeperConnectionException {
    Watcher watcher = zkClient.registerExpirationHandler(new Command() {
      @Override public void execute() {
        /*
         * First rewatch all of our locally cached children.  Some of them may not exist anymore,
         * which will lead to caught KeeperException.NoNode whereafter we'll remove that child
         * from the cached map.
         *
         * Next, we'll establish our top level child watch and add any new nodes that might exist.
         */
        try {
          synchronized (safeToRewatchLock) {
            if (safeToRewatch) {
              rewatchDataNodes();
              tryWatchChildren();
            }
          }
        } catch (InterruptedException e) {
          LOG.log(Level.WARNING, "Interrupted while trying to re-establish watch.", e);
          Thread.currentThread().interrupt();
        }
      }
    });

    try {
      // Synchronize to prevent the race of watchChildren completing and then the session expiring
      // before we update safeToRewatch.
      synchronized (safeToRewatchLock) {
        watchChildren();
        safeToRewatch = true;
      }
    } catch (InterruptedException e) {
      zkClient.unregister(watcher);
      throw e;
    } catch (KeeperException e) {
      zkClient.unregister(watcher);
      throw e;
    } catch (ZooKeeperConnectionException e) {
      zkClient.unregister(watcher);
      throw e;
    }
  }

  @Override
  protected Map<String, V> delegate() {
    return unmodifiableLocalMap;
  }

  private void tryWatchChildren() throws InterruptedException {
    backoffHelper.doUntilSuccess(new ExceptionalSupplier<Boolean, InterruptedException>() {
      @Override public Boolean get() throws InterruptedException {
        try {
          watchChildren();
          return true;
        } catch (KeeperException e) {
          return false;
        } catch (ZooKeeperConnectionException e) {
          return false;
        }
      }
    });
  }

  private synchronized void watchChildren()
      throws InterruptedException, KeeperException, ZooKeeperConnectionException {

    /*
     * Add a watch on the parent node itself, and attempt to rewatch if it
     * gets deleted
     */
    zkClient.get().exists(nodePath, new Watcher() {
      @Override public void process(WatchedEvent event) {
        if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
          // If the parent node no longer exists
          localMap.clear();
          try {
            tryWatchChildren();
          } catch (InterruptedException e) {
            LOG.log(Level.WARNING, "Interrupted while trying to watch children.", e);
            Thread.currentThread().interrupt();
          }
        }
      }});

    final Watcher childWatcher = new Watcher() {
      @Override
      public void process(WatchedEvent event) {
        if (event.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
          try {
            tryWatchChildren();
          } catch (InterruptedException e) {
            LOG.log(Level.WARNING, "Interrupted while trying to watch children.", e);
            Thread.currentThread().interrupt();
          }
        }
      }
    };

    List<String> children = zkClient.get().getChildren(nodePath, childWatcher);
    updateChildren(Sets.newHashSet(children));
  }

  private void tryAddChild(final String child) throws InterruptedException {
    backoffHelper.doUntilSuccess(new ExceptionalSupplier<Boolean, InterruptedException>() {
      @Override public Boolean get() throws InterruptedException {
        try {
          addChild(child);
          return true;
        } catch (KeeperException e) {
          return false;
        } catch (ZooKeeperConnectionException e) {
          return false;
        }
      }
    });
  }

  // TODO(Adam Samet) - Make this use the ZooKeeperNode class.
  private void addChild(final String child)
      throws InterruptedException, KeeperException, ZooKeeperConnectionException {

    final Watcher nodeWatcher = new Watcher() {
      @Override
      public void process(WatchedEvent event) {
        if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
          try {
            tryAddChild(child);
          } catch (InterruptedException e) {
            LOG.log(Level.WARNING, "Interrupted while trying to add a child.", e);
            Thread.currentThread().interrupt();
          }
        } else if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
          removeEntry(child);
        }
      }
    };

    try {
      V value = deserializer.apply(zkClient.get().getData(makePath(child), nodeWatcher, null));
      putEntry(child, value);
    } catch (KeeperException.NoNodeException e) {
      // This node doesn't exist anymore, remove it from the map and we're done.
      removeEntry(child);
    }
  }

  @VisibleForTesting
  void removeEntry(String key) {
    localMap.remove(key);
    mapListener.nodeRemoved(key);
  }

  @VisibleForTesting
  void putEntry(String key, V value) {
    localMap.put(key, value);
    mapListener.nodeChanged(key, value);
  }

  private void rewatchDataNodes() throws InterruptedException {
    for (String child : keySet()) {
      tryAddChild(child);
    }
  }

  private String makePath(final String child) {
    return nodePath + "/" + child;
  }

  private void updateChildren(Set<String> zkChildren) throws InterruptedException {
    Set<String> addedChildren = Sets.difference(zkChildren, keySet());
    Set<String> removedChildren = Sets.difference(keySet(), zkChildren);
    for (String child : addedChildren) {
      tryAddChild(child);
    }
    for (String child : removedChildren) {
      removeEntry(child);
    }
  }
}

