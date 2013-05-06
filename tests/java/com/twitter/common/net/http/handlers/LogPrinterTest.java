package com.twitter.common.net.http.handlers;

import java.io.File;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Test for the LogPrinter.
 *
 * @author William Farner
 */
public class LogPrinterTest {

  @Test
  public void testRelativeFileHandling() {
    LogPrinter printer = new LogPrinter(new File("/this/is/the/log/dir"), true);
    LogPrinter.LogFile absFile = printer.new LogFile("/absolute/path.log");
    assertEquals("/absolute/path.log", absFile.getPath());
    LogPrinter.LogFile relFile = printer.new LogFile("relative/file.log");
    assertEquals("/this/is/the/log/dir/relative/file.log", relFile.getPath());
  }

  @Test
  public void testFilterLines() {
    testFilterLinesHelper(TEST_LINES, FILTER0, FILTERED_LINES0);
    testFilterLinesHelper(TEST_LINES, FILTER1, FILTERED_LINES1);
    testFilterLinesHelper(TEST_LINES, FILTER2, FILTERED_LINES2);
    testFilterLinesHelper(TEST_LINES, FILTER3, FILTERED_LINES3);
  }

  private void testFilterLinesHelper(List<String> testLines,
                                     String filter,
                                     List<String> expectedLines) {

    List<String> filteredLines = Lists.newArrayList(
      LogPrinter.filterLines(Joiner.on("\n").join(testLines), filter).split("\n"));

    assertThat(filteredLines, is(expectedLines));
  }

  private static final List<String> TEST_LINES = Lists.newArrayList(
      "Matching line 1 twittttter",
      "Matching line 2 twitter",
      "Not matching line 1 twiter",
      "Matching line 3"
  );

  private static final String FILTER0 = "";
  private static final List<String> FILTERED_LINES0 = TEST_LINES;

  private static final String FILTER1 = "Matching.*";
  private static final List<String> FILTERED_LINES1 = Lists.newArrayList(
      "Matching line 1 twittttter",
      "Matching line 2 twitter",
      "Matching line 3"
  );

  private static final String FILTER2 = "^.*twitt+er$";
  private static final List<String> FILTERED_LINES2 = Lists.newArrayList(
      "Matching line 1 twittttter",
      "Matching line 2 twitter"
  );

  private static final String FILTER3 = "^.*\\d.*$";
  private static final List<String> FILTERED_LINES3 = TEST_LINES;
}
