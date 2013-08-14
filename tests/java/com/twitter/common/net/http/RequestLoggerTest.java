package com.twitter.common.net.http;

import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.HttpHeaders;
import org.mortbay.jetty.HttpURI;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.RequestLog;
import org.mortbay.jetty.Response;

import com.twitter.common.net.http.RequestLogger.LogSink;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.util.testing.FakeClock;

import static org.easymock.EasyMock.expect;

public class RequestLoggerTest extends EasyMockTest {

  private FakeClock clock;
  private LogSink sink;
  private Request request;
  private Response response;

  private RequestLog log;

  @Before
  public void setUp() throws Exception {
    clock = new FakeClock();
    sink = createMock(LogSink.class);
    request = createMock(Request.class);
    response = createMock(Response.class);
    log = new RequestLogger(clock, sink);
  }

  @Test
  public void testFormat200() throws Exception {
    clock.advance(Amount.of(40L * 365, Time.DAYS));

    expect(response.getStatus()).andReturn(200).atLeastOnce();
    expect(request.getServerName()).andReturn("snoopy");
    expect(request.getHeader(HttpHeaders.X_FORWARDED_FOR)).andReturn(null);
    expect(request.getMethod()).andReturn("GET");
    expect(request.getUri()).andReturn(new HttpURI("/"));
    expect(request.getProtocol()).andReturn("http");
    expect(response.getContentCount()).andReturn(256L);
    expect(request.getRemoteAddr()).andReturn("easymock-test");
    expect(request.getHeader(HttpHeaders.REFERER)).andReturn(null);
    expect(request.getHeader(HttpHeaders.USER_AGENT)).andReturn("junit");
    expect(request.getTimeStamp()).andReturn(clock.nowMillis()).atLeastOnce();

    expect(sink.isLoggable(Level.FINE)).andReturn(true);
    sink.log(Level.FINE, "snoopy easymock-test [22/Dec/2009:00:00:00 +0000]"
        + " \"GET / http\" 200 256 \"-\" \"junit\" 110");

    control.replay();

    clock.advance(Amount.of(110L, Time.MILLISECONDS));
    log.log(request, response);
  }

  @Test
  public void testFormat500() throws Exception {
    clock.advance(Amount.of(40L * 365, Time.DAYS));

    expect(response.getStatus()).andReturn(500).atLeastOnce();
    expect(request.getServerName()).andReturn("woodstock");
    expect(request.getHeader(HttpHeaders.X_FORWARDED_FOR)).andReturn(null);
    expect(request.getMethod()).andReturn("POST");
    expect(request.getUri()).andReturn(new HttpURI("/data"));
    expect(request.getProtocol()).andReturn("http");
    expect(response.getContentCount()).andReturn(128L);
    expect(request.getRemoteAddr()).andReturn("easymock-test");
    expect(request.getHeader(HttpHeaders.REFERER)).andReturn(null);
    expect(request.getHeader(HttpHeaders.USER_AGENT)).andReturn("junit");
    expect(request.getTimeStamp()).andReturn(clock.nowMillis()).atLeastOnce();

    expect(sink.isLoggable(Level.INFO)).andReturn(true);
    sink.log(Level.INFO, "woodstock easymock-test [22/Dec/2009:00:00:00 +0000]"
        + " \"POST /data http\" 500 128 \"-\" \"junit\" 500");

    control.replay();

    clock.advance(Amount.of(500L, Time.MILLISECONDS));
    log.log(request, response);
  }
}
