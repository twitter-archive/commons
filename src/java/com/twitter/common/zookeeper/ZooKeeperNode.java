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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.twitter.common.base.*;
import com.twitter.common.util.BackoffHelper;
import com.twitter.common.zookeeper.ZooKeeperClient.ZooKeeperConnectionException;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * An implementation of {@link Supplier} that offers a readonly view of a
 * zookeeper data node.  This class is thread-safe.
 *
 * Instances of this class each maintain a zookeeper watch for the remote data node.  Instances
 * of this class should be created via the {@link #create} factory method.
 *
 * Please see zookeeper documentation and talk to your cluster administrator for guidance on
 * appropriate node size and total number of nodes you should be using.
 *
 * @param <T> the type of data this node stores
 *
 * @author Adam Samet
 */
public class ZooKeeperNode<T> implements Supplier<T> {
  /**
   * Deserializer for the constructor if you want to simply store the zookeeper byte[] data
   * as-is.
   */
  public static final Function<byte[], byte[]> BYTE_ARRAY_VALUE = Functions.identity();

  private static final Logger LOG = Logger.getLogger(ZooKeeperNode.class.getName());

  private final ZooKeeperClient zkClient;
  private final String nodePath;
  private final Function<byte[], T> deserializer;

  private final BackoffHelper backoffHelper;

  // Whether it's safe to re-establish watches if our zookeeper session has expired.
  private final Object safeToRewatchLock;
  private volatile boolean safeToRewatch;

  private final T NO_DATA = null;
  @Nullable private volatile T nodeData;
  private final Closure<T> dataUpdateListener;

  /**
   * Returns an initialized ZooKeeperNode.  The given node must exist at the time of object
   * creation or a {@link KeeperException} will be thrown.
   *
   * @param zkClient a zookeeper client
   * @param nodePath path to a node whose data will be watched
   * @param deserializer a function that converts byte[] data from a zk node to this supplier's
   *     type T
   *
   * @throws InterruptedException if the underlying zookeeper server transaction is interrupted
   * @throws KeeperException.NoNodeException if the given nodePath doesn't exist
   * @throws KeeperException if the server signals an error
   * @throws ZooKeeperConnectionException if there was a problem connecting to the zookeeper
   *     cluster
   */
  public static <T> ZooKeeperNode<T> create(ZooKeeperClient zkClient, String nodePath,
      Function<byte[], T> deserializer) throws InterruptedException, KeeperException,
      ZooKeeperConnectionException {
    return create(zkClient, nodePath, deserializer, Closures.<T>noop());
  }

  /**
   * Like the above, but optionally takes in a {@link Closure} that will get notified
   * whenever the data is updated from the remote node.
   *
   * @param dataUpdateListener a {@link Closure} to receive data update notifications.
   */
  public static <T> ZooKeeperNode<T> create(ZooKeeperClient zkClient, String nodePath,
      Function<byte[], T> deserializer, Closure<T> dataUpdateListener) throws InterruptedException,
      KeeperException, ZooKeeperConnectionException {
    ZooKeeperNode<T> zkNode =
        new ZooKeeperNode<T>(zkClient, nodePath, deserializer, dataUpdateListener);
    zkNode.init();
    return zkNode;
  }

  /**
   * Initializes a ZooKeeperNode.  The given node must exist at the time of object creation or
   * a {@link KeeperException} will be thrown.
   *
   * Please note that this object will not track any remote zookeeper data until {@link #init()}
   * is successfully called.  After construction and before that call, this {@link Supplier} will
   * return null.
   *
   * @param zkClient a zookeeper client
   * @param nodePath path to a node whose data will be watched
   * @param deserializer a function that converts byte[] data from a zk node to this supplier's
   *     type T
   * @param dataUpdateListener a {@link Closure} to receive data update notifications.
   */
  @VisibleForTesting
  ZooKeeperNode(ZooKeeperClient zkClient, String nodePath,
      Function<byte[], T> deserializer, Closure<T> dataUpdateListener) {
    this.zkClient = Preconditions.checkNotNull(zkClient);
    this.nodePath = MorePreconditions.checkNotBlank(nodePath);
    this.deserializer = Preconditions.checkNotNull(deserializer);
    this.dataUpdateListener = Preconditions.checkNotNull(dataUpdateListener);

    backoffHelper = new BackoffHelper();
    safeToRewatchLock = new Object();
    safeToRewatch = false;
    nodeData = NO_DATA;
  }

  /**
   * Initialize zookeeper tracking for this {@link Supplier}.  Once this call returns, this object
   * will be tracking data in zookeeper.
   *
   * @throws InterruptedException if the underlying zookeeper server transaction is interrupted
   * @throws KeeperException if the server signals an error
   * @throws ZooKeeperConnectionException if there was a problem connecting to the zookeeper
   *     cluster
   */
  @VisibleForTesting
  void init() throws InterruptedException, KeeperException,
      ZooKeeperConnectionException {
    Watcher watcher = zkClient.registerExpirationHandler(new Command() {
      @Override public void execute() {
        try {
          synchronized (safeToRewatchLock) {
            if (safeToRewatch) {
              tryWatchDataNode();
            }
          }
        } catch (InterruptedException e) {
          LOG.log(Level.WARNING, "Interrupted while trying to re-establish watch.", e);
        }
      }
    });

    try {
      /*
       * Synchronize to prevent the race of watchDataNode completing and then the session expiring
       * before we update safeToRewatch.
       */
      synchronized (safeToRewatchLock) {
        watchDataNode();
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

  /**
   * Returns the data corresponding to a byte array in a remote zookeeper node.  This data is
   * cached locally and updated in the background on watch notifications.
   *
   * @return the data currently cached locally or null if {@link #init()} hasn't been called
   *   or the backing node has no data or does not exist anymore.
   */
  @Override
  public @Nullable T get() {
    return nodeData;
  }

  @VisibleForTesting
  void updateData(@Nullable T newData) {
    nodeData = newData;
    dataUpdateListener.execute(newData);
  }

  private void tryWatchDataNode() throws InterruptedException {
    backoffHelper.doUntilSuccess(new ExceptionalSupplier<Boolean, InterruptedException>() {
      @Override public Boolean get() throws InterruptedException {
        try {
          watchDataNode();
          return true;
        } catch (KeeperException e) {
          return false;
        } catch (ZooKeeperConnectionException e) {
          return false;
        }
      }
    });
  }

  private void watchDataNode() throws InterruptedException, KeeperException,
      ZooKeeperConnectionException {
    final Watcher nodeWatcher = new Watcher() {
      @Override
      public void process(WatchedEvent event) {
        if (event.getType() == Watcher.Event.EventType.NodeDataChanged ||
            event.getType() == Watcher.Event.EventType.NodeDeleted) {
          try {
            tryWatchDataNode();
          } catch (InterruptedException e) {
            LOG.log(Level.WARNING, "Interrupted while trying to watch a data node.", e);
          }
        }
      }
    };

    try {
      T newData = deserializer.apply(zkClient.get().getData(nodePath, nodeWatcher, null));
      updateData(newData);
    } catch (KeeperException.NoNodeException e) {
      /*
       * This node doesn't exist right now, reflect that locally and then create a watch to wait
       * for its recreation.
       */
      updateData(NO_DATA);
      watchForExistence();
    }
  }

  private void watchForExistence() throws InterruptedException, KeeperException,
      ZooKeeperConnectionException {
    final Watcher watcher = new Watcher() {
      @Override
      public void process(WatchedEvent event) {
        if (event.getType() == Watcher.Event.EventType.NodeCreated) {
          try {
            tryWatchDataNode();
          } catch (InterruptedException e) {
            LOG.log(Level.WARNING, "Interrupted while trying to watch a data node.", e);
          }
        }
      }
    };

    /*
     * If the node was created between the getData call and this call, just try watching it.
     * We'll have an extra exists watch on it that goes off on its next deletion, which will
     * be a no-op.
     * Otherwise, just let the exists watch wait for its creation.
     */
    if (zkClient.get().exists(nodePath, watcher) != null) {
      tryWatchDataNode();
    }
  }
}

