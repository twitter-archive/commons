package com.twitter.common.logging;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Franco Callari
 * @author Keith Tsui.
 */
public class RootLogConfigTest {
  private static class FakeHandler extends Handler {
    boolean hasPublished = false;
    FakeHandler() { reset(); }
    void reset() { hasPublished = false; }
    public void publish(LogRecord record) { hasPublished = true; }
    public void flush() {}
    public void close() throws SecurityException {}
  }

  // Dummy classes used by the tests for --vmodule.
  private static class ClassA {
    static Logger logger = Logger.getLogger(ClassA.class.getName());
  }

  private static class ClassB {
    static Logger logger = Logger.getLogger(ClassB.class.getName());
  }

  private ByteArrayOutputStream fakeErrorLog;
  private PrintStream errPrintStream;
  Logger fakeRootLogger, testLogger;
  FakeHandler fakeFileLog;
  ConsoleHandler consoleHandler;

  private void assertHasLoggedToStderr() {
    errPrintStream.flush();
    assertTrue(fakeErrorLog.size() > 0);
  }

  private void assertHasNotLoggedToStderr() {
    errPrintStream.flush();
    assertEquals(fakeErrorLog.size(), 0);
  }

  private void assertHasLoggedToFile() {
    assertTrue(fakeFileLog.hasPublished);
  }

  private void assertHasNotLoggedToFile() {
    assertFalse(fakeFileLog.hasPublished);
  }

  // Passes if logger log at least at the given level, directing to stderr.
  private void assertErrorLogAtLevel(Logger logger, Level level) {
    List<Level> levels = Arrays.asList(
        Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG,
        Level.FINE, Level.FINER, Level.FINEST);
    for (Level l : levels) {
      logger.log(l, "Message");
      if (level.intValue() <= l.intValue()) {
        assertHasLoggedToStderr();
      } else {
        assertHasNotLoggedToStderr();
      }
      resetLogs();
    }
  }

  // Passes if logger does not allow verbose logging.
  private void assertNoVerboseLogging(Logger logger) {
    logger.config("Config");
    logger.fine("Fine");
    logger.finer("Finer");
    logger.finest("Finest");
    assertHasNotLoggedToStderr();
    assertHasNotLoggedToFile();
  }

  private void resetLogs() {
    errPrintStream.flush();
    fakeErrorLog.reset();
    fakeFileLog.reset();
  }

  // The following two methods are used to inject our fake root logger, so to avoid test flakyness
  // due to background threads.
  private RootLogConfig.Builder getConfig() {
    return RootLogConfig.builder().rootLoggerName(fakeRootLogger.getName());
  }

  private void setFakeRootForLogger(Logger logger) {
    Preconditions.checkArgument(logger != fakeRootLogger);
    logger.setUseParentHandlers(true);
    logger.setParent(fakeRootLogger);
  }

  @Before
  public void setUp() {
    // Intercept stderr (this must be done first).
    fakeErrorLog = new ByteArrayOutputStream();
    errPrintStream = new PrintStream(fakeErrorLog);
    System.setErr(errPrintStream);

    // Create other members
    consoleHandler = new ConsoleHandler();
    fakeFileLog = new FakeHandler();

    // Emulate default setup (just a console handler), but avoiding the use
    // of the global root logger so not to get a flaky test due to background threads.
    fakeRootLogger = Logger.getLogger("FakeRoot-" + UUID.randomUUID().toString());
    fakeRootLogger.setUseParentHandlers(false);
    for (Handler h : fakeRootLogger.getHandlers()) {
      fakeRootLogger.removeHandler(h);
    }
    fakeRootLogger.addHandler(consoleHandler);

    testLogger = Logger.getLogger(RootLogConfigTest.class.getName());
    testLogger.setUseParentHandlers(true);
    testLogger.setParent(fakeRootLogger);

    setFakeRootForLogger(ClassA.logger);
    setFakeRootForLogger(ClassB.logger);

    resetLogs();
  }

  @Test
  public void testDefaultConfig() {
    // Verify that default info, warning, severe logging goes to stderr, no logging below info.
    assertErrorLogAtLevel(testLogger, Level.INFO);
    resetLogs();
    assertNoVerboseLogging(testLogger);
  }

  @Test
  public void testLogToStderr() {
    // Add a fake handler, verify that it works along with the console.
    fakeRootLogger.addHandler(fakeFileLog);
    testLogger.info("Info");
    assertHasLoggedToStderr();
    assertHasLoggedToFile();

    // Configure logtostderr
    getConfig().logToStderr(true).build().apply();
    resetLogs();

    // Verify that severe, warning, info logs go to stderr only.
    testLogger.severe("Severe");
    assertHasLoggedToStderr();
    assertHasNotLoggedToFile();
    resetLogs();
    testLogger.warning("Warning");
    assertHasLoggedToStderr();
    assertHasNotLoggedToFile();
    resetLogs();
    testLogger.info("Info");
    assertHasLoggedToStderr();
    assertHasNotLoggedToFile();
    resetLogs();

    assertNoVerboseLogging(testLogger);
  }

  @Test
  public void testAlsoLogToStderr() {
    // Add a fake handler, remove console handler, verify that it works.
    fakeRootLogger.removeHandler(consoleHandler);
    fakeRootLogger.addHandler(fakeFileLog);
    testLogger.info("Info");
    assertHasNotLoggedToStderr();
    assertHasLoggedToFile();
    resetLogs();

    // Configure alsologtostderr
    getConfig().alsoLogToStderr(true).build().apply();
    resetLogs();

    // Verify that severe, warning, info logs go to both.
    testLogger.severe("Severe");
    assertHasLoggedToStderr();
    assertHasLoggedToFile();
    resetLogs();
    testLogger.warning("Warning");
    assertHasLoggedToStderr();
    assertHasLoggedToFile();
    resetLogs();
    testLogger.info("Info");
    assertHasLoggedToStderr();
    assertHasLoggedToFile();
    resetLogs();

    assertNoVerboseLogging(testLogger);
  }

  @Test
  public void testLogToStderrOverridesAlsoLogToStderr() {
    // Add a fake handler, remove console handler, verify that it works.
    fakeRootLogger.removeHandler(consoleHandler);
    fakeRootLogger.addHandler(fakeFileLog);
    testLogger.info("Info");
    assertHasNotLoggedToStderr();
    assertHasLoggedToFile();

    // Configure with logtostderr AND alsologtostderr
    getConfig().logToStderr(true).alsoLogToStderr(true).build().apply();
    resetLogs();

    // Verify that severe, warning, info logs go to stderr only.
    testLogger.severe("Severe");
    assertHasLoggedToStderr();
    assertHasNotLoggedToFile();
    resetLogs();
    testLogger.warning("Warning");
    assertHasLoggedToStderr();
    assertHasNotLoggedToFile();
    resetLogs();
    testLogger.info("Info");
    assertHasLoggedToStderr();
    assertHasNotLoggedToFile();
    resetLogs();

    assertNoVerboseLogging(testLogger);
  }

  @Test
  public void testUseGLogFormatter() {
    // Configure glogformatter. We test in "logtostderr" mode so to verify correct formatting
    // for both handlers.
    getConfig().logToStderr(true).useGLogFormatter(true).build().apply();
    resetLogs();

    testLogger.severe("Severe Log Message");
    assertHasLoggedToStderr();
    String output = fakeErrorLog.toString();
    // Verify that it is all in one line and chope the \n.
    assertTrue(output.split("\n").length == 1);
    assertTrue(output.endsWith("\n"));
    output = output.replaceAll("\n", "");

    // Verify that it is on glog format.
    assertTrue("Unexpected output: " + output,
        output.matches("E\\d+ " // Level, month, day.
            + "\\d\\d:\\d\\d:\\d\\d\\.\\d+ " // Timestamp.
            + "THREAD\\d+ " // Thread id.
            + RootLogConfigTest.class.getName() + "\\.testUseGLogFormatter: " // Class name.
            + "Severe Log Message" // Message.
        ));
  }

  @Test
  public void testVlog() {
    // Configure with logtoStderr and vlog==FINE;
    getConfig().logToStderr(true).vlog(RootLogConfig.LogLevel.FINE).build().apply();
    resetLogs();

    // Verify logging at levels fine and above.
    assertErrorLogAtLevel(testLogger, Level.FINE);
  }

  @Test
  public void testVModule() {
    // Configure with ClassA using FINE and ClassB using WARNING;
    Map<Class<?>, RootLogConfig.LogLevel> vmoduleMap =
        ImmutableMap.of(ClassA.class, RootLogConfig.LogLevel.FINE,
                        ClassB.class, RootLogConfig.LogLevel.WARNING);
    getConfig().logToStderr(true).vmodule(vmoduleMap).build().apply();
    resetLogs();

    // No verbose logging other than in ClassA and ClassB.
    assertNoVerboseLogging(testLogger);

    // ClassA logs at FINE and above
    assertErrorLogAtLevel(ClassA.logger, Level.FINE);
    resetLogs();

    // ClassB logs at WARNING and above
    assertErrorLogAtLevel(ClassB.logger, Level.WARNING);
  }

  @Test
  public void testVModuleOverridesVlog() {
    // Configure with ClassA using FINE and ClassB using FINER;
    Map<Class<?>, RootLogConfig.LogLevel> vmoduleMap =
        ImmutableMap.of(ClassA.class, RootLogConfig.LogLevel.FINEST,
                        ClassB.class, RootLogConfig.LogLevel.INFO);
    // Configure setting default vlog=FINER
    getConfig()
        .logToStderr(true).vlog(RootLogConfig.LogLevel.FINER).vmodule(vmoduleMap).build().apply();
    resetLogs();

    // Default logging is at finer level.
    assertErrorLogAtLevel(testLogger, Level.FINER);

    // ClassA logs at FINEST and above
    assertErrorLogAtLevel(ClassA.logger, Level.FINEST);
    resetLogs();

    // ClassB logs at INFO and above
    assertErrorLogAtLevel(ClassB.logger, Level.INFO);
  }
}
