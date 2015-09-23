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

import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.log4j.spi.LoggerRepository;

/**
 * A JUL LogManager which takes over the logging configuration and redirects all messages to Log4j.
 *
 * The approach is inspired by the apache-jul-log4j-bridge project from Paul Smith
 * (<psmith@apache.org>) and available at <a
 * href="http://people.apache.org/~psmith/logging.apache.org/sandbox/jul-log4j-bridge/" />
 *
 * During initialization, it resets configuration and adds a default handler to the root logger to
 * perform the redirection. It also sets the root logger level to the Log4j repository threshold.
 * This implies that Log4j is properly configured before this manager is taking over.
 *
 * To install this log manager, simply add the following property to the java command line:
 * <code>-Djava.util.logging.manager=com.twitter.common.logging.julbridge.JULBridgeLogManager</code>
 *
 * It is possible to configure using extended location information (source filename and line info)
 * by adding the following property to the java command line:
 * <code>-Dcom.twitter.common.logging.julbridge.use-extended-location-info=true</code>
 *
 */
public final class JULBridgeLogManager extends LogManager {
  /**
   * System property name to control if log messages sent from JUL to log4j should contain
   * extended location information.
   *
   * Set @value to true to add source filename and line number to each message.
   */
  public static final String USE_EXTENDED_LOCATION_INFO_PROPERTYNAME =
      "com.twitter.common.logging.julbridge.use-extended-location-info";

  /*
   * LogManager requires a public no-arg constructor to be present so a new instance can be created
   * when configured using the system property. A private constructor will throw an exception.
   */
  public JULBridgeLogManager() {}

  @Override
  public void readConfiguration() {
    assimilate(org.apache.log4j.LogManager.getLoggerRepository());
  }

  /**
   * Assimilates an existing JUL log manager. Equivalent to calling
   * {@link #assimilate(LoggerRepository)} with <code>LogManager.getLoggerRepository</code>.
   */
  public static void assimilate() {
    assimilate(org.apache.log4j.LogManager.getLoggerRepository());
  }

  /**
   * Assimilates an existing JUL log manager.
   *
   * It resets the manager configuration, and adds a bridge handler to the root logger. Messages are
   * redirected to the specified Log4j logger repository.
   *
   * @param loggerRepository the Log4j logger repository to use to redirect messages
   */
  public static void assimilate(LoggerRepository loggerRepository) {
    LogManager.getLogManager().reset();

    boolean withExtendedLocationInfos =
        Boolean.getBoolean(USE_EXTENDED_LOCATION_INFO_PROPERTYNAME);

    Logger rootLogger = Logger.getLogger("");
    rootLogger.setLevel(JULBridgeLevelConverter.fromLog4jLevel(loggerRepository.getThreshold()));
    rootLogger.addHandler(new JULBridgeHandler(loggerRepository, withExtendedLocationInfos));
  }
}
