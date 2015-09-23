// =================================================================================================
// Copyright 2013 Twitter, Inc.
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

package com.twitter.common.zookeeper.guice.client.flagged;

import java.net.InetSocketAddress;
import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotEmpty;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.zookeeper.ZooKeeperClient;
import com.twitter.common.zookeeper.ZooKeeperClient.Credentials;
import com.twitter.common.zookeeper.ZooKeeperUtils;
import com.twitter.common.zookeeper.guice.client.ZooKeeperClientModule.ClientConfig;

/**
 * A factory that creates a {@link ClientConfig} instance based on command line argument values.
 */
public class FlaggedClientConfig {
  @CmdLine(name = "zk_in_proc",
      help = "Launches an embedded zookeeper server for local testing causing -zk_endpoints "
          + "to be ignored if specified.")
  private static final Arg<Boolean> IN_PROCESS = Arg.create(false);

  @NotEmpty
  @CmdLine(name = "zk_endpoints", help ="Endpoint specification for the ZooKeeper servers.")
  private static final Arg<List<InetSocketAddress>> ZK_ENDPOINTS = Arg.create();

  @CmdLine(name = "zk_chroot_path", help = "chroot path to use for the ZooKeeper connections")
  private static final Arg<String> CHROOT_PATH = Arg.create(null);

  @CmdLine(name = "zk_session_timeout", help ="The ZooKeeper session timeout.")
  private static final Arg<Amount<Integer, Time>> SESSION_TIMEOUT =
      Arg.create(ZooKeeperUtils.DEFAULT_ZK_SESSION_TIMEOUT);

  @CmdLine(name = "zk_digest_credentials",
           help ="user:password to use when authenticating with ZooKeeper.")
  private static final Arg<String> DIGEST_CREDENTIALS = Arg.create();

  /**
   * Creates a configuration from command line arguments.
   *
   * @return Configuration instance.
   */
  public static ClientConfig create() {
    return new ClientConfig(
        ZK_ENDPOINTS.get(),
        Optional.fromNullable(CHROOT_PATH.get()),
        IN_PROCESS.get(),
        SESSION_TIMEOUT.get(),
        DIGEST_CREDENTIALS.hasAppliedValue()
            ? getCredentials(DIGEST_CREDENTIALS.get())
            : Credentials.NONE
    );
  }

  private static Credentials getCredentials(String userAndPass) {
    List<String> parts = ImmutableList.copyOf(Splitter.on(":").split(userAndPass));
    if (parts.size() != 2) {
      throw new IllegalArgumentException(
          "zk_digest_credentials must be formatted as user:pass");
    }
    return ZooKeeperClient.digestCredentials(parts.get(0), parts.get(1));
  }
}
