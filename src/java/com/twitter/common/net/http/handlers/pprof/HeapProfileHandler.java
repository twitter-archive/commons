package com.twitter.common.net.http.handlers.pprof;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.io.Closeables;

import com.twitter.common.net.http.handlers.HttpServletRequestParams;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;

/**
 * A handler that collects heap allocation profile for the running application
 * using the Heapster agent lib, and replies in a format recognizable by
 * gperftools: http://code.google.com/p/gperftools
 */
public class HeapProfileHandler extends HttpServlet {
  private static final Logger LOG = Logger.getLogger(HeapProfileHandler.class.getName());

  private final HeapsterHelper heapster;

  public HeapProfileHandler() {
    super();
    HeapsterHelper helper = null;
    try {
      helper = new HeapsterHelper(ClassLoader.getSystemClassLoader().loadClass("Heapster"));
    } catch (ClassNotFoundException e) {
      // If your runtime environment cannot load the class, the profiler will return an
      // error message each time it is used.
      LOG.warning("Continuing without heapster profiling, could not load Heapster class: " + e);
    }
    this.heapster = helper;
  }

  /**
   * HeapsterHelper uses reflection to provide more readable access to the Heapster class,
   * which we dynamically class load.  Reflection is necessary as the Heapster class will
   * likely not be on the classpath.
   */
  private class HeapsterHelper {
    final Class klass;

    private HeapsterHelper(Class klass) {
      this.klass = klass;
    }

    private void start()
        throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
      klass.getDeclaredMethod("start").invoke(null);
    }

    private void stop()
        throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
      klass.getDeclaredMethod("stop").invoke(null);
    }

    private byte[] dumpProfile(Boolean forceGC)
        throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
      return (byte[]) klass.getDeclaredMethod("dumpProfile", Boolean.class).invoke(null, forceGC);
    }

    private void clearProfile()
        throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
      klass.getDeclaredMethod("clearProfile").invoke(null);
    }

    private void setSamplingPeriod(Integer period)
        throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
      klass.getDeclaredMethod("setSamplingPeriod", Integer.class).invoke(null, period);
    }

    private byte[] profile(int durationSeconds, int samplingPeriodBytes, boolean forceGC)
        throws InvocationTargetException, NoSuchMethodException, IllegalAccessException,
               InterruptedException {
      clearProfile();
      setSamplingPeriod(samplingPeriodBytes);
      start();
      Thread.sleep(durationSeconds * 1000L);
      stop();
      return dumpProfile(forceGC);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // This will happen if the class loader failed to load Heapster at construction time.
    if (heapster == null) {
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Heapster not loaded!");
      return;
    }

    // Default duration is 10 seconds, default period is 5MB.
    final int profileDurationSecs = HttpServletRequestParams.getInt(req, "seconds", 10);
    final int profileSamplingPeriodBytes =
        HttpServletRequestParams.getInt(req, "sample_period", Amount.of(5, Data.MB).as(Data.BYTES));

    final boolean forceGC = HttpServletRequestParams.getBool(req, "force_gc", true);
    LOG.info("Collecting heap allocation profile for " + profileDurationSecs + " seconds every " +
             profileSamplingPeriodBytes + " bytes, with forceGC? " + forceGC);

    byte[] profileBytes;
    try {
      profileBytes = heapster.profile(profileDurationSecs, profileSamplingPeriodBytes, forceGC);
      LOG.info("Profile contains " + profileBytes.length + " bytes");
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Exception while profiling", e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      return;
    }

    resp.setHeader("Content-Type", "pprof/raw");
    resp.setStatus(HttpServletResponse.SC_OK);
    OutputStream responseBody = resp.getOutputStream();
    try {
      responseBody.write(profileBytes);
    } finally {
      Closeables.close(responseBody, /* swallowIOException */ true);
    }
  }
}
