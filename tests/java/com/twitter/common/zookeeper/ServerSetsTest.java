package com.twitter.common.zookeeper;

import com.google.common.collect.ImmutableMap;

import com.twitter.common.io.Codec;
import com.twitter.thrift.Endpoint;
import com.twitter.thrift.ServiceInstance;
import com.twitter.thrift.Status;

import java.net.InetSocketAddress;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServerSetsTest {
  @Test
  public void testSimpleSerialization() throws Exception {
    InetSocketAddress endpoint = new InetSocketAddress(12345);
    Map<String, Endpoint > additionalEndpoints = ImmutableMap.of();
    Status status = Status.ALIVE;

    Codec<ServiceInstance> codec = ServerSetImpl.createDefaultCodec();

    byte[] data = ServerSets.serializeServiceInstance(
        endpoint, additionalEndpoints, status, codec);

    ServiceInstance instance = ServerSets.deserializeServiceInstance(data, codec);

    assertEquals(endpoint.getPort(), instance.getServiceEndpoint().getPort());
    assertEquals(additionalEndpoints, instance.getAdditionalEndpoints());
    assertEquals(Status.ALIVE, instance.getStatus());
  }
}
