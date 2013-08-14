package com.twitter.common.net.http.handlers;

import javax.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

public class HttpServletRequestParamsTest extends EasyMockTest {
  private static final String INT_PARAM = "int_param";
  private static final String LONG_PARAM = "long_param";
  private static final String STRING_PARAM = "string_param";
  private static final String UNSET_PARAM = "unset_param";
  private static final String BOOL_PARAM = "bool_param";

  private HttpServletRequest request;

  @Before
  public void setUp() throws Exception {
    request = createMock(HttpServletRequest.class);
    expect(request.getParameter(INT_PARAM)).andReturn("123").anyTimes();
    expect(request.getParameter(LONG_PARAM)).andReturn("260833376896966656").anyTimes();
    expect(request.getParameter(STRING_PARAM)).andReturn("asdf").anyTimes();
    expect(request.getParameter(UNSET_PARAM)).andReturn(null).anyTimes();
    expect(request.getParameter(BOOL_PARAM)).andReturn("TRUE").anyTimes();
  }

  @Test
  public void testGetIntParam() {
    EasyMock.replay(request);
    assertEquals(123, HttpServletRequestParams.getInt(request, INT_PARAM, 456));
    assertEquals(456, HttpServletRequestParams.getInt(request, STRING_PARAM, 456));
    assertEquals(456, HttpServletRequestParams.getInt(request, UNSET_PARAM, 456));
    assertEquals(456, HttpServletRequestParams.getInt(request, LONG_PARAM, 456));
  }

  @Test
  public void testGetLongParam() {
    EasyMock.replay(request);
    assertEquals(123, HttpServletRequestParams.getLong(request, INT_PARAM, 456));
    assertEquals(260833376896966656L, HttpServletRequestParams.getLong(request, LONG_PARAM, 456));
    assertEquals(123456789012345678L,
                 HttpServletRequestParams.getLong(request, STRING_PARAM, 123456789012345678L));
    assertEquals(456, HttpServletRequestParams.getLong(request, UNSET_PARAM, 456));
  }

  @Test
  public void testGetStringParam() {
    EasyMock.replay(request);
    assertEquals("123", HttpServletRequestParams.getString(request, INT_PARAM, "default"));
    assertEquals("260833376896966656",
                 HttpServletRequestParams.getString(request, LONG_PARAM, "default"));
    assertEquals("asdf", HttpServletRequestParams.getString(request, STRING_PARAM, "default"));
    assertEquals("default", HttpServletRequestParams.getString(request, UNSET_PARAM, "default"));
  }

  @Test
  public void testGetBoolParam() {
    EasyMock.replay(request);
    assertEquals(false, HttpServletRequestParams.getBool(request, INT_PARAM, true));
    assertEquals(false, HttpServletRequestParams.getBool(request, LONG_PARAM, false));
    assertEquals(true, HttpServletRequestParams.getBool(request, BOOL_PARAM, false));
  }
}
