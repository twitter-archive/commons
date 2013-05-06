namespace java com.twitter.common.zookeeper.testing.angrybird.gen
namespace py gen.twitter.common.zookeeper.testing.angrybird


enum ResponseCode {
  OK    = 0,
  ERROR	= 1
}

struct TestEndpoint {
  1: string nodeId
  2: string host
  3: i32 port
}

struct ExpireSessionRequest {
  1: i64 sessionId
}

// Hostname and port of the endpoint.
struct ExpireEndpointRequest {
  1: string host
  2: i32 port
}

// Expire a zookeeper group leader
struct ExpireLeaderRequest {
  1: string zkPath
}

// Expire a zookeeper group follower
// If nodeId is not specified, expire a random follower.
struct ExpireFollowerRequest {
  1: string zkPath
  2: optional string nodeId
}

// Response for a session expiration request.
// sessionId: session id of the session expired successfully.
struct ExpireResponse {
  1: ResponseCode responseCode
  2: i64 sessionId
}

// Response for startup request.
struct StartupResponse {
  1: ResponseCode responseCode
  2: string responseMessage
}

// Response for restart request.
struct RestartResponse {
  1: ResponseCode responseCode
  2: string responseMessage
}

// Response for shutdown request.
struct ShutdownResponse {
  1: ResponseCode responseCode
  2: string responseMessage
}

// This response contains the port of the zookeeper server.
struct ServerPortResponse {
  1: ResponseCode responseCode
  2: i32 port
}

service ZooKeeperThriftServer {
  // Returns the port of the zookeeper server.
  ServerPortResponse getZooKeeperServerPort()

  // Expires a session based upon sessionId
  ExpireResponse expireSession(1: ExpireSessionRequest expireRequest)

  // Expires a session based upon a host/port pair found in any znode in the ensemble.
  ExpireResponse expireEndpoint(1: ExpireEndpointRequest expireRequest)

  // Expires a session based upon the minimum sequence id found beneath zkPath.
  ExpireResponse expireLeader(1: ExpireLeaderRequest expireRequest)

  // Expires a session based upon the ids encoded in ephemeral znodes beneath zkPath.
  ExpireResponse expireFollower(1: ExpireFollowerRequest expireRequest)

  // Starts up a zk server.  May only be called if the server is currently down.
  StartupResponse startup()

  // Restarts a zk server.  May be called at any time.
  RestartResponse restart()

  // Shuts down the zk server.  May be called at any time.
  ShutdownResponse shutdown()
}
