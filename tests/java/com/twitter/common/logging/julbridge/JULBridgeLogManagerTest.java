// =================================================================================================
// Copyright 2013 Twitter, Inc.
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

package com.twitter.common.logging.julbridge;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Hierarchy;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.RootLogger;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JULBridgeLogManagerTest {

  @After
  public void restoreLogConfiguration() throws SecurityException, IOException {
    LogManager.getLogManager().readConfiguration();
  }

  @Test
  public void checkAssimilateTakesOver() {
    // Create a test log4j environment
    final List<LoggingEvent> events = new LinkedList<LoggingEvent>();

    org.apache.log4j.Logger log4jRoot = new RootLogger(org.apache.log4j.Level.ALL);
    LoggerRepository loggerRepository = new Hierarchy(log4jRoot);
    loggerRepository.setThreshold(org.apache.log4j.Level.INFO);

    log4jRoot.addAppender(new AppenderSkeleton() {
      @Override public boolean requiresLayout() {
        return false;
      }

      @Override public void close() {}

      @Override protected void append(LoggingEvent event) {
        events.add(event);
      }
    });


    JULBridgeLogManager.assimilate(loggerRepository);

    Logger.getLogger("test.1").log(Level.INFO, "test message 1");
    Logger.getLogger("test.2").log(Level.FINE, "test message 2");
    Logger.getLogger("test.3").log(Level.WARNING, "test message 3");

    assertThat(events.size(), is(2));
    assertThat(events.get(0).getLoggerName(), is("test.1"));
    assertThat(events.get(0).getMessage(), is((Object) "test message 1"));
    assertThat(events.get(1).getLoggerName(), is("test.3"));
    assertThat(events.get(1).getMessage(), is((Object) "test message 3"));
  }
}
