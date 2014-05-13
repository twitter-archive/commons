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

package com.twitter.common.logging.scribe;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

import org.apache.scribe.LogEntry;
import org.apache.scribe.ResultCode;
import org.apache.scribe.scribe;
import org.apache.thrift.TException;

import com.twitter.common.logging.Log;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.thrift.Thrift;
import com.twitter.common.thrift.ThriftFactory;

/**
 * Implementation of the scribe client, logs message directly to scribe.
 *
 * @author William Farner
 */
public class ScribeLog implements Log<LogEntry, ResultCode> {
  private static final Logger LOG = Logger.getLogger(ScribeLog.class.getName());

  // Connection pool options.
  private static final int MAX_CONNECTIONS_PER_HOST = 5;
  private static final Amount<Long, Time> REQUEST_TIMEOUT = Amount.of(4L, Time.SECONDS);

  // Max retries per request before giving up.
  private static final int MAX_RETRIES = 3;

  private final scribe.Iface client;

  /**
   * Equivalent to {@link #ScribeLog(List, int)}
   * with a {@code maxConnections} of 5.
   */
  public ScribeLog(List<InetSocketAddress> hosts) throws ThriftFactory.ThriftFactoryException {
    this(hosts, MAX_CONNECTIONS_PER_HOST);
  }

  /**
   * Creates a new scribe client, connecting to the given hosts on the given port.
   *
   * @param hosts Thrift servers to connect to.
   * @param maxConnections Max connections allowed for the log client.
   * @throws ThriftFactory.ThriftFactoryException If the client could not be created.
   */
  public ScribeLog(List<InetSocketAddress> hosts, int maxConnections)
      throws ThriftFactory.ThriftFactoryException {
    Preconditions.checkNotNull(hosts);

    Thrift<scribe.Iface> thrift = ThriftFactory.create(scribe.Iface.class)
        .withMaxConnectionsPerEndpoint(maxConnections)
        .useFramedTransport(true)
        .build(Sets.newHashSet(hosts));

    client = thrift.builder()
        .withRetries(MAX_RETRIES)
        .withRequestTimeout(REQUEST_TIMEOUT)
        .create();
  }

  @Override
  public ResultCode log(LogEntry entry) {
    return log(Arrays.asList(entry));
  }

  @Override
  public ResultCode log(List<LogEntry> entries) {
    try {
      return client.Log(entries);
    } catch (TException e) {
      LOG.log(Level.WARNING, "Failed to submit log request!.", e);
      return ResultCode.TRY_LATER;
    }
  }

  @Override
  public void flush() {
    // No-op.
  }

  public static final Predicate<ResultCode> RETRY_FILTER = new Predicate<ResultCode>() {
    @Override public boolean apply(ResultCode result) {
      return result != ResultCode.OK;
    }
  };
}
