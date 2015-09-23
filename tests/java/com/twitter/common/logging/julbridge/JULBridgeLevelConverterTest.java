package com.twitter.common.logging.julbridge;

import java.util.Arrays;
import java.util.logging.Level;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.twitter.common.logging.julbridge.JULBridgeLevelConverter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JULBridgeLevelConverterTest {

  @RunWith(Parameterized.class)
  public static class JULMappingToLog4JTest {
    @SuppressWarnings("serial")
    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          { Level.FINEST,  org.apache.log4j.Level.TRACE },
          { Level.FINER,   org.apache.log4j.Level.DEBUG },
          { Level.FINE,    org.apache.log4j.Level.DEBUG },
          { Level.INFO,    org.apache.log4j.Level.INFO },
          { Level.WARNING, org.apache.log4j.Level.WARN },
          { Level.SEVERE,  org.apache.log4j.Level.ERROR },
          { Level.ALL,     org.apache.log4j.Level.ALL },
          { Level.OFF,     org.apache.log4j.Level.OFF },
          // Unknown level should map to DEBUG
          { new Level("test", 42) {}, org.apache.log4j.Level.DEBUG }
      });
    }

    private final Level level;
    private final org.apache.log4j.Level expected;

    public JULMappingToLog4JTest(Level level, org.apache.log4j.Level expected) {
      this.level = level;
      this.expected = expected;
    }

    @Test
    public void checkJULMapsToLog4J() {
      assertThat(JULBridgeLevelConverter.toLog4jLevel(level), is(expected));
    }
  }

  @RunWith(Parameterized.class)
  public static class Log4JMappingToJULTest {
    @SuppressWarnings("serial")
    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          { org.apache.log4j.Level.TRACE, Level.FINEST },
          { org.apache.log4j.Level.DEBUG, Level.FINE },
          { org.apache.log4j.Level.INFO,  Level.INFO },
          { org.apache.log4j.Level.WARN,  Level.WARNING },
          { org.apache.log4j.Level.ERROR, Level.SEVERE },
          { org.apache.log4j.Level.FATAL, Level.SEVERE },
          { org.apache.log4j.Level.ALL,   Level.ALL },
          { org.apache.log4j.Level.OFF,   Level.OFF },
          // Unknown level should map to FINE
          { new org.apache.log4j.Level(42, "test", 42) {}, Level.FINE }
      });
    }

    private final org.apache.log4j.Level level;
    private final Level expected;

    public Log4JMappingToJULTest(org.apache.log4j.Level level, Level expected) {
      this.level = level;
      this.expected = expected;
    }

    @Test
    public void checkJULMapsToLog4J() {
      assertThat(JULBridgeLevelConverter.fromLog4jLevel(level), is(expected));
    }
  }
}
