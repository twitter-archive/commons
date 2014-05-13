package com.twitter.common.net.http.filters;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.net.http.HttpServerDispatch;
import com.twitter.common.net.http.JettyHttpServerDispatch;
import com.twitter.common.stats.Stat;
import com.twitter.common.stats.Stats;
import com.twitter.common.util.Clock;
import com.twitter.common.util.testing.FakeClock;

import static com.sun.jersey.api.core.ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HttpStatsFilterIntegrationTest {
  private Client client;
  private FakeClock clock;
  private JettyHttpServerDispatch server;

  @Before
  public void setUp() {
    Stats.flush();

    server = new JettyHttpServerDispatch();
    server.listen(0);
    server.registerFilter(GuiceFilter.class, "/*");

    clock = new FakeClock();

    final Injector injector = Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(TestServlet.class).in(Singleton.class);

            bind(Clock.class).toInstance(clock);
            bind(HttpStatsFilter.class).in(Singleton.class);
          }
        },
        new JerseyServletModule() {
          @Override
          protected void configureServlets() {
            filter("/*").through(HttpStatsFilter.class);
            serve("/*").with(GuiceContainer.class, ImmutableMap.of(
                PROPERTY_CONTAINER_RESPONSE_FILTERS, HttpStatsFilter.class.getName()));
          }
        }
    );
    server.getRootContext().addEventListener(new GuiceServletContextListener() {
      @Override protected Injector getInjector() {
        return injector;
      }
    });

    ClientConfig config = new DefaultClientConfig();
    client = Client.create(config);
  }

  @Path("/")
  public static class TestServlet {
    @GET
    @Path("/hello")
    @Produces(MediaType.TEXT_PLAIN)
    @HttpStatsFilter.TrackRequestStats("hello")
    public Response hello() {
      return Response.ok("hello world").build();
    }

    @GET
    @Path("/hola")
    @Produces(MediaType.TEXT_PLAIN)
    @HttpStatsFilter.TrackRequestStats("hola")
    public Response hola() {
      return Response.ok("hola mundo").build();
    }

    @GET
    @Path("/goodbye")
    @Produces(MediaType.TEXT_PLAIN)
    public Response goodbye() {
      return Response.ok("goodbye cruel world").build();
    }
  }

  private String getResource(String path) {
    return client
        .resource(String.format("http://localhost:%s%s", server.getPort(), path))
        .accept(MediaType.TEXT_PLAIN)
        .get(String.class);
  }

  private void assertStatValue(String statName, long expectedValue) {
    Stat<Long> stat = Stats.getVariable(statName);
    assertEquals(expectedValue, stat.read().longValue());
  }

  @Test
  public void testStatsTracking() throws Exception {
    getResource("/hello");

    assertStatValue("http_hello_200_responses_events", 1);
  }

  @Test
  public void testRepeatedContextInjection() throws Exception {
    getResource("/hello");
    getResource("/hola");
    getResource("/hello");

    assertStatValue("http_hello_200_responses_events", 2);
    assertStatValue("http_hola_200_responses_events", 1);
  }

  @Test
  public void testNoStatsTracking() throws Exception {
    getResource("/goodbye");

    assertNull(Stats.getVariable("http_goodbye_200_responses_events"));
  }

  @Test
  public void testNoMatchedMethod() throws Exception {
    try {
      getResource("/what");
      fail("Should have thrown a 404.");
    } catch (UniformInterfaceException e) {
      assertStatValue("http_404_responses_events", 1);
    }
  }
}
