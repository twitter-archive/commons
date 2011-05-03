// =================================================================================================
// Copyright 2011 Twitter, Inc.
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

package com.twitter.common.logging;

import org.junit.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class LogUtilTest {

  @Test
  public void testEmptyPattern() {
    test(null, LogUtil.DEFAULT_LOG_DIR);
    test("", LogUtil.DEFAULT_LOG_DIR);
  }

  @Test
  public void testLocalDir() {
    test(".", ".");
    test("./asdf.%g.log", ".");
    test("asdf.%g.log", ".");
  }

  @Test
  public void testRelativeDir() {
    test("../asdf.%g.log", "..");
    test("b/asdf.%g.log", "b");
    test("b/c/d/asdf.%g.log", "b/c/d");
  }

  @Test
  public void testAbsoluteDir() {
    test("/a/b/c/d/logs.%g.%u.log", "/a/b/c/d");
    test("/asdf.log", "/");
  }

  private void test(String pattern, String expected) {
    assertThat(LogUtil.getLogManagerLogDir(pattern).getPath(), is(expected));
  }

  private void test(String pattern, File expected) {
    test(pattern, expected.getPath());
  }
}
