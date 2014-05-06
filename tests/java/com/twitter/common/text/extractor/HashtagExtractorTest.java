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

package com.twitter.common.text.extractor;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.twitter.common.text.token.TwitterTokenStream;

public class HashtagExtractorTest {
  private TwitterTokenStream stream;

  @Before
  public void setup() {
    stream = new HashtagExtractor();
  }

  @Test
  public void testSingleHashtag() {
    stream.reset("this is a #hashtag");
    assertEquals(ImmutableList.of("#hashtag"), stream.toStringList());

    // Note, hashtag should not contain the training dot.
    stream.reset("this is a #hashtag.");
    assertEquals(ImmutableList.of("#hashtag"), stream.toStringList());

    stream.reset("#thatswhatshesaid haha... #funny");
    assertEquals(ImmutableList.of("#thatswhatshesaid", "#funny"), stream.toStringList());
  }
}
