package com.twitter.common.zookeeper.testing.angrybird;

import java.io.IOException;
import java.text.ParseException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.ZKDatabase;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.zookeeper.testing.ZooKeeperTestServer;

import com.twitter.common.zookeeper.testing.angrybird.gen.TestEndpoint;

/**
 * ZooKeeper server harness for fault testing.
 *
 * You may expire sessions directly by SessionId, by Endpoint (host, port pair) or
 * by leader/follower in a group of ephemeral/sequential nodes.
 *
 * The Endpoint encoding currently assumes that the format of the content in the znode is an
 * "id@host:port" string.
 */
public class AngryBirdZooKeeperServer extends ZooKeeperTestServer {

  private static final Logger LOG = Logger.getLogger(AngryBirdZooKeeperServer.class.getName());
  private static final Splitter AT_SPLITTER = Splitter.on('@').trimResults().omitEmptyStrings();

  public AngryBirdZooKeeperServer(int port, ShutdownRegistry shutdownRegistry) throws IOException {
    super(port, shutdownRegistry);
  }

  /**
   * Expires the zookeeper session id.
   *
   * @param sessionId The Zookeeper session id
   * @return the sessionId if the session was successfully closed
   */
  public final Optional<Long> expireSession(Long sessionId) {
    return closeSession(Optional.of(sessionId));
  }

  /**
   * Expires the zookeeper session of the given endpoint.
   * For now, this only supports those endpoints that store their host:port in the znode.
   *
   * @param host host address of the endpoint stored in the znode
   * @param port port of the endpoint stored in the znode
   * @return the session id of the znode that matches the endpoint if a match is found
   */
  public final Optional<Long> expireEndpoint(String host, int port) {
    return closeSession(getSessionIdFromHostPair(host, port));
  }

  /**
   * Expires zookeeper session of leader rooted at a znode.
   *
   * @param path the zookeeper path that is used for leader election
   * @return the session id of the matching candidate if a match is found
   */
  public final Optional<Long> expireLeader(String path) {
    return closeSession(getLeaderSessionIdFromPath(path));
  }

  /**
   * Expires zookeeper session of follower rooted at a znode.
   *
   * @param path the zookeeper path that is used for leader election
   * @param nodeId (optional) expire a specific follower node if found
   * @return the session id of the matching candidate if a match is found
   */
  public final Optional<Long> expireFollower(String path, Optional<String> nodeId) {
    return closeSession(getFollowerSessionIdFromPath(path, nodeId));
  }

  private Optional<Long> closeSession(Optional<Long> sessionId) {
    if (!sessionId.isPresent()) {
      LOG.warning("No session found for expiration!");
    } else {
      LOG.info("Closing session: " + sessionId.get());
      zooKeeperServer.closeSession(sessionId.get().longValue());
    }

    return sessionId;
  }

  /**
   * Returns the session whose corresponding znode encodes "host:port"
   *
   * @param host ip address of the endpoint
   * @param port endpoint port
   * @return session id of the corresponding zk session if a match is found.
   */
  private Optional<Long> getSessionIdFromHostPair(String host, int port) {
    // TODO(vinod): Instead of (host, port) args use the more generic byte[] as args
    // so that comparison can be made on znodes that are ServerSet ephemerals
    ZKDatabase zkDb = zooKeeperServer.getZKDatabase();

    for (long sessionId : zkDb.getSessions()) {
      for (String path: zkDb.getEphemerals(sessionId)) {
        LOG.info("SessionId:" + sessionId + " Path:" + path);
        try {
          String data = new String(zkDb.getData(path, new Stat(), null));
          LOG.info("Data in znode: " + data);

          TestEndpoint endpoint = parseEndpoint(data);
          LOG.info("Extracted endpoint " + endpoint);

          if (endpoint.getHost().equals(host) && endpoint.getPort() == port) {
            LOG.info(String.format(
                "Matching session id %s found for endpoint %s:%s", sessionId, host, port));
            return Optional.of(sessionId);
          }
        } catch (NoNodeException e) {
          LOG.severe("Exception getting data for Path:" + path + " : " + e);
        } catch (ParseException e) {
          LOG.severe("Exception parsing data: " + e);
        } catch (NumberFormatException e) {
          LOG.severe("Exception in url format " + e);
        }
      }
    }

    return Optional.absent();
  }

  /**
   * Return the session id of the leader candidate.
   * See http://zookeeper.apache.org/doc/trunk/recipes.html#sc_leaderElection
   *
   * @param zkPath Znode path prefix of the candidates
   * @return the session id of the corresponding zk session if a match is found.
   */
  private Optional<Long> getLeaderSessionIdFromPath(String zkPath) {
    ZKDatabase zkDb = zooKeeperServer.getZKDatabase();
    Long leaderSessionId = null;
    Long masterSeq = Long.MAX_VALUE;

    // Reg-ex pattern for sequence numbers in znode paths.
    Pattern pattern = Pattern.compile("\\d+$");

    // First find the session id of the leading scheduler.
    for (long sessionId : zkDb.getSessions()) {
      for (String path: zkDb.getEphemerals(sessionId)) {
        if (StringUtils.startsWith(path, zkPath)) {
          try {
            // Get the sequence number.
            Matcher matcher = pattern.matcher(path);
            if (matcher.find()) {
              LOG.info("Pattern matched path: " + path + " session: " + sessionId);
              Long seq = Long.parseLong(matcher.group());
              if (seq < masterSeq) {
                masterSeq = seq;
                leaderSessionId = sessionId;
              }
            }
          } catch (NumberFormatException e) {
            LOG.severe("Exception formatting sequence number " + e);
          }
        }
      }
    }

    if (leaderSessionId != null) {
      LOG.info(String.format("Found session leader for %s: %s", zkPath, leaderSessionId));
    }

    return Optional.of(leaderSessionId);
  }

  /**
   * Return the session id of a follower candidate
   *
   * @param zkPath Znode path prefix of the candidates
   * @param candidateId (optional) specific candidate id of follower to expire, otherwise random.
   * @return session id of the corresponding zk session if a match is found
   */
  private Optional<Long> getFollowerSessionIdFromPath(String zkPath, Optional<String> nodeId) {
    Optional<Long> leaderSessionId = getLeaderSessionIdFromPath(zkPath);
    if (!leaderSessionId.isPresent()) {
      return leaderSessionId;
    }

    ZKDatabase zkDb = zooKeeperServer.getZKDatabase();

    for (long sessionId : zkDb.getSessions()) {
      if (sessionId == leaderSessionId.get()) {
        continue;
      }
      for (String path: zkDb.getEphemerals(sessionId)) {
        if (StringUtils.startsWith(path, zkPath)) {
          LOG.info(String.format("Found session follower for %s: %s", zkPath, sessionId));
          if (!nodeId.isPresent()) {
            return Optional.of(sessionId);
          } else {
            TestEndpoint endpoint;
            try {
              endpoint = parseEndpoint(new String(zkDb.getData(path, new Stat(), null)));
              if (endpoint.getNodeId().equals(nodeId.get())) {
                return Optional.of(sessionId);
              }
            } catch (ParseException e) {
              LOG.severe("Failed to parse endpoint " + path + ": " + e);
            } catch (NoNodeException e) {
              LOG.severe("Exception getting data for Path:" + path + " :" + e);
            }
          }
        }
      }
    }

    return Optional.absent();
  }

  private TestEndpoint parseEndpoint(String data) throws ParseException {
    ImmutableList<String> endpointComponents = ImmutableList.copyOf(AT_SPLITTER.split(data));
    if (endpointComponents.size() != 2) {
      throw new ParseException("Unknown znode data: Expected format id@host:port", 0);
    }

    String nodeId = endpointComponents.get(0);
    HostAndPort pair;
    try {
      pair = HostAndPort.fromString(endpointComponents.get(1));
    } catch (IllegalArgumentException e) {
      throw new ParseException("Failed to parse endpoint data: " + endpointComponents.get(1),
          data.indexOf('@'));
    }

    TestEndpoint endpoint = new TestEndpoint();
    endpoint.setNodeId(nodeId);
    endpoint.setHost(pair.getHostText());
    endpoint.setPort(pair.getPort());
    return endpoint;
  }
}
