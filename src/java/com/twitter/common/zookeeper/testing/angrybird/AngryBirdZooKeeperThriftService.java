package com.twitter.common.zookeeper.testing.angrybird;

import java.io.IOException;

import com.google.common.base.Optional;

import com.twitter.common.zookeeper.testing.angrybird.gen.ExpireEndpointRequest;
import com.twitter.common.zookeeper.testing.angrybird.gen.ExpireFollowerRequest;
import com.twitter.common.zookeeper.testing.angrybird.gen.ExpireLeaderRequest;
import com.twitter.common.zookeeper.testing.angrybird.gen.ExpireSessionRequest;
import com.twitter.common.zookeeper.testing.angrybird.gen.ExpireResponse;
import com.twitter.common.zookeeper.testing.angrybird.gen.RestartResponse;
import com.twitter.common.zookeeper.testing.angrybird.gen.ServerPortResponse;
import com.twitter.common.zookeeper.testing.angrybird.gen.ShutdownResponse;
import com.twitter.common.zookeeper.testing.angrybird.gen.StartupResponse;
import com.twitter.common.zookeeper.testing.angrybird.gen.ZooKeeperThriftServer;
import com.twitter.util.Future;

import static com.twitter.common.zookeeper.testing.angrybird.gen.ResponseCode.ERROR;
import static com.twitter.common.zookeeper.testing.angrybird.gen.ResponseCode.OK;

/**
 * Thrift interface for the angrybird ZooKeeper server.
 */
public class AngryBirdZooKeeperThriftService implements ZooKeeperThriftServer.ServiceIface {

  private final AngryBirdZooKeeperServer zkServer;

  private Future<ExpireResponse> sessionIdToExpireResponse(Optional<Long> sessionId) {
    ExpireResponse response = new ExpireResponse();

    if (sessionId.isPresent()) {
      response.setResponseCode(OK).setSessionId(sessionId.get().longValue());
    } else {
      response.setResponseCode(ERROR).setSessionId(0);
    }

    return Future.value(response);
  }

  /**
   * Creates a new angrybird thrift server
   *
   * @param zkServer Thrift server to interact with.
   */
  public AngryBirdZooKeeperThriftService(AngryBirdZooKeeperServer zkServer) {
    this.zkServer = zkServer;
  }

  @Override
  public Future<ServerPortResponse> getZooKeeperServerPort() {
    ServerPortResponse response = new ServerPortResponse();
    int port = zkServer.getPort();
    return Future.value(response.setResponseCode(OK).setPort(port));
  }

  @Override
  public Future<ExpireResponse> expireSession(ExpireSessionRequest expireRequest) {
    return sessionIdToExpireResponse(zkServer.expireSession(expireRequest.sessionId));
  }

  @Override
  public Future<ExpireResponse> expireEndpoint(ExpireEndpointRequest expireRequest) {
    return sessionIdToExpireResponse(
        zkServer.expireEndpoint(expireRequest.host, expireRequest.port));
  }

  @Override
  public Future<ExpireResponse> expireLeader(ExpireLeaderRequest request) {
    return sessionIdToExpireResponse(zkServer.expireLeader(request.zkPath));
  }

  @Override
  public Future<ExpireResponse> expireFollower(ExpireFollowerRequest request) {
    return sessionIdToExpireResponse(zkServer.expireFollower(request.zkPath, 
                                     Optional.fromNullable(request.getNodeId())));
  }

  @Override
  public Future<StartupResponse> startup() {
    StartupResponse response = new StartupResponse();

    try {
      zkServer.startNetwork();
      response.setResponseCode(OK);
      response.setResponseMessage("OK");
    } catch (IOException e) {
      response.setResponseCode(ERROR).setResponseMessage(e.getMessage());
    } catch (InterruptedException e) {
      response.setResponseCode(ERROR).setResponseMessage(e.getMessage());
    }
    return Future.value(response);
  }

  @Override
  public Future<ShutdownResponse> shutdown() {
    ShutdownResponse response = new ShutdownResponse();
    zkServer.shutdownNetwork();
    return Future.value(response.setResponseCode(OK).setResponseMessage("OK"));
  }

  @Override
  public Future<RestartResponse> restart() {
    RestartResponse response = new RestartResponse();

    try {
      zkServer.shutdownNetwork();
      zkServer.restartNetwork();
      response.setResponseCode(OK).setResponseMessage("OK");
    } catch (IOException e) {
      response.setResponseCode(ERROR).setResponseMessage(e.getMessage());
    } catch (InterruptedException e) {
      response.setResponseCode(ERROR).setResponseMessage(e.getMessage());
    }
    return Future.value(response);
  }
}
