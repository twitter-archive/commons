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

package com.twitter.common.net;

import com.google.common.base.Function;
import com.google.common.testing.TearDown;
import com.google.common.testing.junit4.TearDownTestCase;
import com.twitter.common.base.Closure;
import com.twitter.common.collections.Pair;
import org.easymock.Capture;
import org.easymock.IAnswer;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO(John Sirois): add test for error conditions
 *
 * @author John Sirois
 */
public class UrlResolverUtilTest extends TearDownTestCase {
  private static final String REDIRECT_LOCATION = "http://bar";

  private IMocksControl control;
  private Function<URL, String> urlToUA;
  private Closure<Pair<HttpServletRequest, HttpServletResponse>> requestHandler;
  private UrlResolverUtil urlResolverUtil;
  private HttpServlet servlet;
  private String url;

  @Before
  public void setUp() {
    control = createControl();

    @SuppressWarnings("unchecked")
    Function<URL, String> urlToUA = control.createMock(Function.class);
    this.urlToUA = urlToUA;

    @SuppressWarnings("unchecked")
    Closure<Pair<HttpServletRequest, HttpServletResponse>> handler =
        control.createMock(Closure.class);
    requestHandler = handler;

    urlResolverUtil = new UrlResolverUtil(urlToUA);

    servlet = new HttpServlet() {
      @Override protected void service(HttpServletRequest req, HttpServletResponse resp)
          throws ServletException, IOException {
        requestHandler.execute(Pair.of(req, resp));
      }
    };
  }

  @Test
  public void testUASelection() throws Exception {
    url = startServer();
    String redirectLocation = "http://bar.com";

    expect(urlToUA.apply(new URL(url))).andReturn("foo-agent");
    expectRequestAndRedirect(redirectLocation);
    control.replay();

    String effectiveUrl = urlResolverUtil.getEffectiveUrl(url, null /* no proxy */);
    assertEquals(redirectLocation, effectiveUrl);

    control.verify();
  }

  @Test
  public void testRelativeRedirect() throws Exception {
    url = startServer();
    String relativeRedirect = "relatively/speaking";

    expect(urlToUA.apply(new URL(url))).andReturn("foo-agent");
    expectRequestAndRedirect(relativeRedirect);
    control.replay();

    String effectiveUrl = urlResolverUtil.getEffectiveUrl(url, null /* no proxy */);
    assertEquals(url + relativeRedirect, effectiveUrl);

    control.verify();
  }

  @Test
  public void testInvalidRedirect() throws Exception {
    url = startServer();
    String badRedirect = ":::<<<<<::IAMNOTAVALIDURI";

    expect(urlToUA.apply(new URL(url))).andReturn("foo-agent");
    expectRequestAndRedirect(badRedirect);
    control.replay();

    String effectiveUrl = urlResolverUtil.getEffectiveUrl(url, null /* no proxy */);
    assertEquals(badRedirect, effectiveUrl);

    control.verify();
  }

  private void expectRequestAndRedirect(final String location) throws Exception {
    final Capture<Pair<HttpServletRequest, HttpServletResponse>> requestCapture =
        new Capture<Pair<HttpServletRequest, HttpServletResponse>>();
    requestHandler.execute(capture(requestCapture));
    expectLastCall().andAnswer(new IAnswer<Void>() {
      @Override public Void answer() throws Throwable {
        assertTrue(requestCapture.hasCaptured());
        Pair<HttpServletRequest, HttpServletResponse> pair = requestCapture.getValue();

        HttpServletRequest request = pair.getFirst();
        assertEquals("HEAD", request.getMethod());
        assertEquals("foo-agent", request.getHeader("User-Agent"));

        pair.getSecond().sendRedirect(location);
        return null;
      }
    });
  }

  private String startServer() throws Exception {
    final Server server = new Server();
    final SocketConnector connector = new SocketConnector();
    connector.setHost("127.0.0.1");
    connector.setPort(0);
    server.addConnector(connector);

    Context context = new Context(server, "/", Context.NO_SECURITY);
    context.addServlet(new ServletHolder(servlet), "/*");
    server.start();
    addTearDown(new TearDown() {
      @Override public void tearDown() throws Exception {
        server.stop();
      }
    });

    return "http://" + connector.getHost() + ":" + connector.getLocalPort() + "/";
  }
}
