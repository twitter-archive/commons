package com.twitter.common.net.http;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;

import org.mortbay.component.AbstractLifeCycle;
import org.mortbay.jetty.HttpHeaders;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.RequestLog;
import org.mortbay.jetty.Response;
import org.mortbay.util.DateCache;

import com.twitter.common.util.Clock;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A request logger that borrows formatting code from {@link org.mortbay.jetty.NCSARequestLog},
 * but removes unneeded features (writing to file) and logging to java.util.logging.
 */
public class RequestLogger extends AbstractLifeCycle implements RequestLog {

  private static final Logger LOG = Logger.getLogger(RequestLogger.class.getName());

  private final Clock clock;
  private final LogSink sink;
  private final DateCache logDateCache;

  interface LogSink {
    boolean isLoggable(Level level);
    void log(Level level, String messagge);
  }

  RequestLogger() {
    this(Clock.SYSTEM_CLOCK, new LogSink() {
      @Override
      public boolean isLoggable(Level level) {
        return LOG.isLoggable(level);
      }

      @Override public void log(Level level, String message) {
        LOG.log(level, message);
      }
    });
  }

  @VisibleForTesting
  RequestLogger(Clock clock, LogSink sink) {
    this.clock = checkNotNull(clock);
    this.sink = checkNotNull(sink);
    logDateCache = new DateCache("dd/MMM/yyyy:HH:mm:ss Z", Locale.getDefault());
    logDateCache.setTimeZoneID("GMT");
  }

  private String formatEntry(Request request, Response response) {
    StringBuilder buf = new StringBuilder();

    buf.append(request.getServerName());
    buf.append(' ');

    String addr = request.getHeader(HttpHeaders.X_FORWARDED_FOR);
    if (addr == null) {
      addr = request.getRemoteAddr();
    }

    buf.append(addr);
    buf.append(" [");
    buf.append(logDateCache.format(request.getTimeStamp()));
    buf.append("] \"");
    buf.append(request.getMethod());
    buf.append(' ');
    buf.append(request.getUri().toString());
    buf.append(' ');
    buf.append(request.getProtocol());
    buf.append("\" ");
    buf.append(response.getStatus());
    buf.append(' ');
    buf.append(response.getContentCount());
    buf.append(' ');

    String referer = request.getHeader(HttpHeaders.REFERER);
    if (referer == null) {
      buf.append("\"-\" ");
    } else {
      buf.append('"');
      buf.append(referer);
      buf.append("\" ");
    }

    String agent = request.getHeader(HttpHeaders.USER_AGENT);
    if (agent == null) {
      buf.append("\"-\" ");
    } else {
      buf.append('"');
      buf.append(agent);
      buf.append('"');
    }

    buf.append(' ');
    buf.append(clock.nowMillis() - request.getTimeStamp());
    return buf.toString();
  }

  @Override
  public void log(Request request, Response response) {
    int statusCategory = response.getStatus() / 100;
    Level level = ((statusCategory == 2) || (statusCategory == 3)) ? Level.FINE : Level.INFO;
    if (!sink.isLoggable(level)) {
      return;
    }

    sink.log(level, formatEntry(request, response));
  }
}
