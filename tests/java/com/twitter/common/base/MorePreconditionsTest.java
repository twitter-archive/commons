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

package com.twitter.common.base;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author John Sirois
 */
public class MorePreconditionsTest {

  @Test(expected = NullPointerException.class)
  public void testCheckNotBlankStringNull() {
    MorePreconditions.checkNotBlank((String) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckNotBlankStringEmpty() {
    MorePreconditions.checkNotBlank("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckNotBlankSpaces() {
    MorePreconditions.checkNotBlank(" ");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckNotBlankWhitespace() {
    MorePreconditions.checkNotBlank("\t\r\n ");
  }

  @Test
  public void testCheckNotBlankStringValid() {
    String argument = new String("foo");
    assertSame(argument, MorePreconditions.checkNotBlank(argument));
  }

  @Test
  public void testCheckNotBlankStringExceptionFormatting() {
    try {
      MorePreconditions.checkNotBlank((String) null, "the meaning of life is %s", 42);
    } catch (NullPointerException e) {
      assertEquals("the meaning of life is 42", e.getMessage());
    }

    try {
      MorePreconditions.checkNotBlank("", "wing beats per second is %s", 43);
    } catch (IllegalArgumentException e) {
      assertEquals("wing beats per second is 43", e.getMessage());
    }
  }

  @Test(expected = NullPointerException.class)
  public void testCheckNotBlankIterableNull() {
    MorePreconditions.checkNotBlank((Iterable<?>) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckNotBlankIterableEmpty() {
    MorePreconditions.checkNotBlank(ImmutableList.<Object>of());
  }

  @Test
  public void testCheckNotBlankIterableValid() {
    ImmutableList<String> argument = ImmutableList.of("");
    ImmutableList<String> result = MorePreconditions.checkNotBlank(argument);
    assertSame(argument, result);
  }

  @Test
  public void testCheckNotBlankIterableExceptionFormatting() {
    try {
      MorePreconditions.checkNotBlank((Iterable<?>) null, "the meaning of life is %s", 42);
    } catch (NullPointerException e) {
      assertEquals("the meaning of life is 42", e.getMessage());
    }

    try {
      MorePreconditions.checkNotBlank(ImmutableList.of(), "wing beats per second is %s", 43);
    } catch (IllegalArgumentException e) {
      assertEquals("wing beats per second is 43", e.getMessage());
    }
  }
}
