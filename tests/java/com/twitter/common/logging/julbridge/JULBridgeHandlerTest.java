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

import java.util.ListResourceBundle;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.apache.log4j.Category;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

import com.twitter.common.logging.julbridge.JULBridgeLevelConverter;
import com.twitter.common.logging.julbridge.JULBridgeHandler;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public class JULBridgeHandlerTest {

  @Test
  public void checkMessageWithParametersIsFormatted() {
    LogRecord record = new LogRecord(Level.FINEST, "test is {0}");
    record.setParameters(new Object[] {"successful"});

    assertThat(JULBridgeHandler.formatMessage(record), is("test is successful"));
  }

  @Test
  public void checkMessageWithResourceBundleIsFormatted() {
    ResourceBundle bundle = new ListResourceBundle() {
      @Override protected Object[][] getContents() {
        return new Object[][] {
            {"test is successful", "le test fonctionne"}
        };
      }
    };

    LogRecord record = new LogRecord(Level.FINEST, "test is successful");
    record.setResourceBundle(bundle);

    assertThat(JULBridgeHandler.formatMessage(record), is("le test fonctionne"));
  }

  @Test
  public void checkGetLoggerReturnsLoggerWithSameName() {
    LogRecord record = new LogRecord(Level.FINEST, "test message");
    record.setLoggerName("test.checkGetLogger");

    assertThat(new JULBridgeHandler().getLogger(record).getName(), is("test.checkGetLogger"));
  }

  @Test
  public void checkToLoggingEvent() {
    LogRecord record = new LogRecord(Level.FINEST, "test is {0}");
    record.setParameters(new Object[] {"successful"});

    record.setThreadID(42);
    Throwable t = new Throwable();
    record.setThrown(t);

    // source class and method names are usually inferred, but because there's no JUL in the stack
    // frame, it won't work as expected.
    record.setSourceClassName(getClass().getName());
    record.setSourceMethodName("checkToLoggingEvent");

    Logger log4jLogger = new JULBridgeHandler().getLogger(record);
    org.apache.log4j.Level log4jLevel = JULBridgeLevelConverter.toLog4jLevel(Level.FINEST);
    LoggingEvent event = JULBridgeHandler.toLoggingEvent(record, log4jLogger, log4jLevel, false);

    assertThat(event.getLogger(), is((Category) log4jLogger));
    assertThat(event.getLevel(), is(log4jLevel));
    assertThat(event.getMessage(), is((Object) "test is successful"));
    assertThat(event.getThreadName(), is("42"));
    assertThat(event.getTimeStamp(), is(record.getMillis()));
    assertThat(event.getThrowableInformation().getThrowable(), is(sameInstance(t)));

    LocationInfo info = event.getLocationInformation();
    assertThat(info.getClassName(), is(getClass().getName()));
    assertThat(info.getMethodName(), is("checkToLoggingEvent"));
  }
}
