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

package com.twitter.common.text.combiner;

import static org.junit.Assert.assertEquals;

import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.twitter.common.text.extractor.RegexExtractor;
import com.twitter.common.text.token.TokenStream;

public class PossessiveTokenCombinerTest {
  private TokenStream stream;

  @Before
  public void setup() {
    stream = new PossessiveTokenCombiner(
        // this toknizes text into alphabet-only, number-only, and apostrophe tokens.
        // This simulates how MeCab tokenizes text.
        new RegexExtractor.Builder()
            .setRegexPattern(Pattern.compile("([0-9]+|[a-zA-Z]+|'|\\.)")).build());
  }

  @Test
  public void testSingleSmily() {
    stream.reset("this is Keita's desk");

    assertEquals(ImmutableList.of("this", "is", "Keita's", "desk"),
        stream.toStringList());

    stream.reset("this desk is Keita's.");

    assertEquals(ImmutableList.of("this", "desk", "is", "Keita's", "."),
        stream.toStringList());
  }
}
