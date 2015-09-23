// =================================================================================================
// Copyright 2013 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.logging.julbridge;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import javax.annotation.Nullable;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * JUL Handler to convert JUL {@link LogRecord} messages into Log4j's {@link LoggingEvent} messages,
 * and route them to a Log4J logger with the same name as the JUL logger.
 */
public class JULBridgeHandler extends Handler {
  private static final String UNKNOWN_LOGGERNAME = "unknown";

  /**
   * Converts a JUL log record into a Log4J logging event.
   *
   * @param record the JUL log record to convert
   * @param logger the Log4J logger to use for the logging event
   * @param level the Log4J level to use for the logging event
   * @param useExtendedLocationInfo if false, do no try to get source file and line informations
   * @return a Log4J logging event
   */
  static LoggingEvent toLoggingEvent(LogRecord record, Logger logger, Level level,
      boolean useExtendedLocationInfo) {

    LocationInfo locationInfo = useExtendedLocationInfo
        ? new LocationInfo(new Throwable(), record.getSourceClassName())
        : new LocationInfo("?", record.getSourceClassName(), record.getSourceMethodName(), "?");

    // Getting thread name from thread id? complicated...
    String threadName = String.valueOf(record.getThreadID());
    ThrowableInformation throwableInformation = record.getThrown() == null
        ? null
        : new ThrowableInformation(record.getThrown());

    return new LoggingEvent(
        record.getSourceClassName(),
        logger,
        record.getMillis(),
        level,
        formatMessage(record),
        threadName,
        throwableInformation,
        null /* ndc */,
        locationInfo,
        null /* properties */);
  }

  /**
   * Formats a log record message in a way similar to {@link Formatter#formatMessage(LogRecord)}.
   *
   * If the record contains a resource bundle, a lookup is done to find a localized version.
   *
   * If the record contains parameters, the message is formatted using
   * {@link MessageFormat#format(String, Object...)}
   *
   * @param record the log record used to format the message
   * @return a formatted string
   */
  static String formatMessage(LogRecord record) {
    String message = record.getMessage();

    // Look for a resource bundle
    java.util.ResourceBundle catalog = record.getResourceBundle();
    if (catalog != null) {
      try {
        message = catalog.getString(record.getMessage());
      } catch (MissingResourceException e) {
        // Not found? Fallback to original message string
        message = record.getMessage();
      }
    }

    Object parameters[] = record.getParameters();
    if (parameters == null || parameters.length == 0) {
      // No parameters? just return the message string
      return message;
    }

    // Try formatting
    try {
      return MessageFormat.format(message, parameters);
    } catch (IllegalArgumentException e) {
      return message;
    }
  }

  private final LoggerRepository loggerRepository;
  private final boolean useExtendedLocationInfo;

  /**
   * Creates a new JUL handler. Equivalent to calling {@link #JULBridgeHandler(boolean)} passing
   * <code>false</code> as argument.
   */
  public JULBridgeHandler() {
    this(LogManager.getLoggerRepository(), false);
  }

  /**
   * Creates a new JUL handler.
   * Equivalent to calling {@link #JULBridgeHandler(LoggerRepository, boolean)} passing
   * <code>LogManager.getLoggerRepository()</code> and <code>useExtendedLocationInfo</code> as
   * arguments.
   *
   * @param useExtendedLocationInfo if true, try to add source filename and line info to log message
   */
  public JULBridgeHandler(boolean useExtendedLocationInfo) {
    this(LogManager.getLoggerRepository(), useExtendedLocationInfo);
  }

  /**
   * Creates a new JUL handler.
   *
   * @param loggerRepository Log4j logger repository where to get loggers from
   * @param useExtendedLocationInfo if true, try to add source filename and line info to log message
   * @throws NullPointerException if loggerRepository is null
   */
  public JULBridgeHandler(LoggerRepository loggerRepository, boolean useExtendedLocationInfo) {
    this.loggerRepository = checkNotNull(loggerRepository);
    this.useExtendedLocationInfo = useExtendedLocationInfo;
  }

  /**
   * Gets a Log4J Logger with the same name as the logger name stored in the log record.
   *
   * @param record a JUL log record
   * @return a Log4J logger with the same name, or name {@value #UNKNOWN_LOGGERNAME} if no name is
   * present in the record.
   */
  Logger getLogger(LogRecord record) {
    String loggerName = record.getLoggerName();
    if (loggerName == null) {
      loggerName = UNKNOWN_LOGGERNAME;
    }

    return loggerRepository.getLogger(loggerName);
  }

  /**
   * Publishes the log record to a Log4J logger of the same name.
   *
   * Before formatting the message, level is converted and message is discarded if Log4j logger is
   * not enabled for that level.
   *
   * @param record the record to publish
   */
  @Override
  public void publish(@Nullable LogRecord record) {
    // Ignore silently null records
    if (record == null) {
      return;
    }

    Logger log4jLogger = getLogger(record);
    Level log4jLevel = JULBridgeLevelConverter.toLog4jLevel(record.getLevel());

    if (log4jLogger.isEnabledFor(log4jLevel)) {
      LoggingEvent event = toLoggingEvent(record, log4jLogger, log4jLevel, useExtendedLocationInfo);

      log4jLogger.callAppenders(event);
    }
  }

  @Override
  public void flush() {}

  @Override
  public void close() {}
}
