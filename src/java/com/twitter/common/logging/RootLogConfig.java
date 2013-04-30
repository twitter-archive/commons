package com.twitter.common.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;

/**
 * A configuration class for the root java.util.logging Logger.
 *
 * Defines flags to control the behavior behavior of the root logger similarly to Google's glog
 * library (see http://code.google.com/p/google-glog ).
 */
public class RootLogConfig {
  /**
   * An enum reflecting log {@link Level} constants.
   */
  public enum LogLevel {
    FINEST(Level.FINEST),
    FINER(Level.FINER),
    FINE(Level.FINE),
    CONFIG(Level.CONFIG),
    INFO(Level.INFO),
    WARNING(Level.WARNING),
    SEVERE(Level.SEVERE);

    private final Level level;

    private LogLevel(Level level) {
      this.level = level;
    }

    private Level getLevel() {
      return level;
    }
  }

  @CmdLine(name = "logtostderr", help = "Log messages to stderr instead of logfiles.")
  private static Arg<Boolean> LOGTOSTDERR = Arg.create(false);

  @CmdLine(name = "alsologtostderr",
           help = "Log messages to stderr, in addition to log files. Ignored when --logtostderr")
  private static Arg<Boolean> ALSOLOGTOSTDERR = Arg.create(false);

  @CmdLine(name = "vlog",
           help = "The value is one of the constants in java.util.logging.Level. "
                  + "Shows all messages with level equal or higher "
                  + "than the value of this flag.")
  private static Arg<LogLevel> VLOG = Arg.create(LogLevel.INFO);

  @CmdLine(name = "vmodule",
           help = "Per-class verbose level. The argument has to contain a comma-separated list "
                  + "of <class_name>=<log_level>. <class_name> is the full-qualified name of a "
                  + "class, <log_level> is one of the constants in java.util.logging.Level. "
                  + "<log_level> overrides any value given by --vlog.")
  private static Arg<Map<Class<?>, LogLevel>> VMODULE =
      Arg.<Map<Class<?>, LogLevel>>create(new HashMap<Class<?>, LogLevel>());

  @CmdLine(name = "use_glog_formatter", help = "True to use the glog formatter exclusively.")
  private static Arg<Boolean> USE_GLOG_FORMATTER = Arg.create(true);

  /**
   * Represents a logging configuration for java.util.logging.
   */
  public static final class Configuration {
    boolean logToStderr = false;
    boolean alsoLogToStderr = false;
    boolean useGLogFormatter = true;
    LogLevel vlog = LogLevel.INFO;
    ImmutableMap<Class<?>, LogLevel> vmodule = ImmutableMap.of();
    String rootLoggerName = "";

    Configuration() {
      // Guard for internal use only.
    }

    /**
     * Returns {@code true} if configured to log just to stderr.
     */
    public boolean isLogToStderr() {
      return logToStderr;
    }

    /**
     * Returns {@code true} if configured to log to stderr in addition to log files..
     */
    public boolean isAlsoLogToStderr() {
      return alsoLogToStderr;
    }

    /**
     * Returns {@code true} if configured to log in google-glog format.
     */
    public boolean isUseGLogFormatter() {
      return useGLogFormatter;
    }

    /**
     * Returns the default global log level.
     */
    public LogLevel getVlog() {
      return vlog;
    }

    /**
     * Returns the custom log levels configured for individual classes.
     */
    public ImmutableMap<Class<?>, LogLevel> getVmodule() {
      return vmodule;
    }

    /**
     * Applies this configuration to the root log.
     */
    public void apply() {
      RootLogConfig.configure(this);
    }
  }

  /**
   * A builder-pattern class used to perform the configuration programmatically
   * (i.e. not through flags).
   * Example:
   * <code>
   *    RootLogConfig.builder().logToStderr(true).build().apply();
   * </code>
   */
  public static final class Builder {
    private final Configuration configuration;

    private Builder() {
      this.configuration = new Configuration();
    }

    /**
     * Only log messages to stderr, instead of log files. Overrides alsologtostderr.
     * Default: false.
     *
     * @param flag True to enable, false to disable.
     * @return this Configuration object.
     */
    public Builder logToStderr(boolean flag) {
      configuration.logToStderr = flag;
      return this;
    }

    /**
     * Also log messages to stderr, in addition to log files.
     * Overridden by logtostderr.
     * Default: false.
     *
     * @param flag True to enable, false to disable.
     * @return this Configuration object.
     */
    public Builder alsoLogToStderr(boolean flag) {
      configuration.alsoLogToStderr = flag;
      return this;
    }

    /**
     * Format log messages in one-line with a header, similar to google-glog.
     * Default: false.
     *
     * @param flag True to enable, false to disable.
     * @return this Configuration object.
     */
    public Builder useGLogFormatter(boolean flag) {
      configuration.useGLogFormatter = flag;
      return this;
    }

    /**
     * Output log messages at least at the given verbosity level.
     * Overridden by vmodule.
     * Default: INFO
     *
     * @param level LogLevel enumerator for the minimum log message verbosity level that is output.
     * @return this Configuration object.
     */
    public Builder vlog(LogLevel level) {
      Preconditions.checkNotNull(level);
      configuration.vlog = level;
      return this;
    }

    /**
     * Output log messages for a given set of classes at the associated verbosity levels.
     * Overrides vlog.
     * Default: no classes are treated specially.
     *
     * @param pairs Map of classes and correspoding log levels.
     * @return this Configuration object.
     */
    public Builder vmodule(Map<Class<?>, LogLevel> pairs) {
      Preconditions.checkNotNull(pairs);
      configuration.vmodule = ImmutableMap.copyOf(pairs);
      return this;
    }

    /**
     * Returns the built up configuration.
     */
    public Configuration build() {
      return configuration;
    }

    // Intercepts the root logger, for testing purposes only.
    @VisibleForTesting
    Builder rootLoggerName(String name) {
      Preconditions.checkNotNull(name);
      Preconditions.checkArgument(!name.isEmpty());
      configuration.rootLoggerName = name;
      return this;
    }
  }

  /**
   * Creates a new Configuration builder object.
   *
   * @return The builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a logging configuration using flags
   *
   * @return The logging configuration specified via command line flags.
   */
  public static Configuration configurationFromFlags() {
    return builder()
        .logToStderr(LOGTOSTDERR.get())
        .alsoLogToStderr(ALSOLOGTOSTDERR.get())
        .useGLogFormatter(USE_GLOG_FORMATTER.get())
        .vlog(VLOG.get())
        .vmodule(VMODULE.get())
        .build();
  }

  private static void configure(Configuration configuration) {
    // Edit the properties of the root logger.
    Logger rootLogger = Logger.getLogger(configuration.rootLoggerName);
    if (configuration.logToStderr) {
      setLoggerToStderr(rootLogger);
    } else if (configuration.alsoLogToStderr) {
      setLoggerToAlsoStderr(rootLogger);
    }
    if (configuration.useGLogFormatter) {
      setGLogFormatter(rootLogger);
    }
    if (configuration.vlog != null) {
      setVlog(rootLogger, configuration.vlog);
    }
    if (configuration.vmodule != null) {
      setVmodules(configuration.vmodule);
    }
  }

  private static void setLoggerToStderr(Logger logger) {
    LogManager.getLogManager().reset();
    setConsoleHandler(logger, true);
  }

  private static void setLoggerToAlsoStderr(Logger logger) {
    setConsoleHandler(logger, false);
  }

  private static void setConsoleHandler(Logger logger, boolean removeOtherHandlers) {
    Handler consoleHandler = null;
    for (Handler h : logger.getHandlers()) {
      if (h instanceof ConsoleHandler) {
        consoleHandler = h;
      } else if (removeOtherHandlers) {
        logger.removeHandler(h);
      }
    }
    if (consoleHandler == null) {
      consoleHandler = new ConsoleHandler();
      logger.addHandler(new ConsoleHandler());
    }
    consoleHandler.setLevel(Level.ALL);
    consoleHandler.setFilter(null);
  }

  private static void setGLogFormatter(Logger logger) {
    for (Handler h : logger.getHandlers()) {
      h.setFormatter(new LogFormatter());
    }
  }

  private static void setVmodules(Map<Class<?>, LogLevel> vmodules) {
    for (Map.Entry<Class<?>, LogLevel> entry : vmodules.entrySet()) {
      String className = entry.getKey().getName();
      Logger logger = Logger.getLogger(className);
      setVlog(logger, entry.getValue());
    }
  }

  private static void setVlog(Logger logger, LogLevel logLevel) {
    final Level newLevel = logLevel.getLevel();
    logger.setLevel(newLevel);
    do {
      for (Handler handler : logger.getHandlers()) {
        Level handlerLevel = handler.getLevel();
        if (newLevel.intValue() < handlerLevel.intValue()) {
          handler.setLevel(newLevel);
        }
      }
    } while (logger.getUseParentHandlers() && (logger = logger.getParent()) != null);
  }

  // Utility class.
  private RootLogConfig() {
  }
}
