package com.twitter.common.net.http.handlers;

import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

/**
 * Simple utility for parsing HttpServletRequest parameters by type.
 */
public class HttpServletRequestParams {
  private static final Logger LOG = Logger.getLogger(HttpServletRequestParams.class.getName());

  /**
   * Parses an int param from an HttpServletRequest, returns a default value
   * if the parameter is not set or is not a valid int.
   */
  public static int getInt(HttpServletRequest request, String param, int defaultValue) {
    final String value = request.getParameter(param);
    int result = defaultValue;
    if (value != null) {
      try {
        result = Integer.parseInt(value);
      } catch (NumberFormatException e) {
        LOG.warning("Invalid int for " + param + ": " + value);
      }
    }
    return result;
  }

  /**
   * Parses a long param from an HttpServletRequest, returns a defualt value
   * if the parameter is not set or is not a valid long.
   */
  public static long getLong(HttpServletRequest request, String param, long defaultValue) {
    final String value = request.getParameter(param);
    long result = defaultValue;
    if (value != null) {
      try {
        result = Long.parseLong(value);
      } catch (NumberFormatException e) {
        LOG.warning("Invalid long for " + param + ": " + value);
      }
    }
    return result;
  }

  /**
   * Parses a bool param from an HttpServletRequest, returns a default value
   * if the parameter is not set.  Note that any value that is set will be
   * considered a legal bool by Boolean.valueOf, defualting to false if not
   * understood.
   */
  public static boolean getBool(HttpServletRequest request, String param, boolean defaultValue) {
    if (request.getParameter(param) != null) {
      return Boolean.valueOf(request.getParameter(param));
    } else {
      return defaultValue;
    }
  }

  /**
   * Returns a string param from an HttpServletRequest if set, returns a defualt value
   * if the parameter is not set.
   */
  @Nullable
  public static String getString(HttpServletRequest request, String param,
                                 @Nullable String defaultValue) {
    if (request.getParameter(param) != null) {
      return request.getParameter(param);
    } else {
      return defaultValue;
    }
  }
}
