// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.net.http.handlers;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Test for the LogPrinter.
 *
 * @author William Farner
 */
public class LogPrinterTest {

  @Test
  public void testFilterLines() {
    test(TEST_LINES, FILTER0, FILTERED_LINES0);
    test(TEST_LINES, FILTER1, FILTERED_LINES1);
    test(TEST_LINES, FILTER2, FILTERED_LINES2);
    test(TEST_LINES, FILTER3, FILTERED_LINES3);
  }

  private void test(List<String> testLines, String filter, List<String> expectedLines) {
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
