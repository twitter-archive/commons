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
import com.twitter.common.text.token.TwitterTokenStream;

public class PossessiveContractionTokenCombinerTest {
  private TwitterTokenStream stream;

  @Before
  public void setup() {
    stream = new PossessiveContractionTokenCombiner(
        // this toknizes text into alphabet-only, number-only, and apostrophe tokens.
        new RegexExtractor.Builder()
            .setRegexPattern(Pattern.compile("([0-9]+|[a-zA-Z]+|'|\\.)")).build());
  }

  @Test
  public void testPossessiveCase() {
    stream.reset("this is Keita's desk");
    assertEquals(ImmutableList.of("this", "is", "Keita's", "desk"),
        stream.toStringList());

    stream.reset("this desk is Keita's.");
    assertEquals(ImmutableList.of("this", "desk", "is", "Keita's", "."),
        stream.toStringList());

    stream.reset("YELLING THIS DESK IS KEITA'S.");
    assertEquals(ImmutableList.of("YELLING", "THIS", "DESK", "IS", "KEITA'S", "."),
        stream.toStringList());
  }

  @Test
  public void testContraction() {
    stream.reset("Don't try while I'm out");
    assertEquals(ImmutableList.of("Don't", "try", "while", "I'm", "out"),
        stream.toStringList());

    stream.reset("Let's ask what we should've done");
    assertEquals(ImmutableList.of("Let's", "ask", "what", "we", "should've", "done"),
        stream.toStringList());

    stream.reset("LET'S YELL WHAT WE SHOULD'VE DONE");
    assertEquals(ImmutableList.of("LET'S", "YELL", "WHAT", "WE", "SHOULD'VE", "DONE"),
        stream.toStringList());
  }

  @Test
  public void testContractionWithSpace() {
    stream.reset("Don 't try while I 'm out");
    assertEquals(ImmutableList.of("Don","'", "t", "try", "while", "I", "'", "m", "out"),
        stream.toStringList());

    stream.reset("Let' s ask what we should' ve done");
    assertEquals(ImmutableList.of("Let", "'", "s", "ask", "what", "we", "should", "'", "ve", "done"),
        stream.toStringList());
  }
}
