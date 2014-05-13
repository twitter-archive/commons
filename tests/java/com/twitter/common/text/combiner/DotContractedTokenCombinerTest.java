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

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import com.twitter.common.text.token.TwitterTokenStream;
import com.twitter.common.text.tokenizer.LatinTokenizer;

/**
 * Unit Test for DotContractedTokenCombiner
 *
 * @author Keita Fujii
 * @author Cindy Lin
 */
public class DotContractedTokenCombinerTest {
  private TwitterTokenStream stream;

  @Before
  public void setup() {
    stream = new DotContractedTokenCombiner(
        new LatinTokenizer.Builder().setKeepPunctuation(true).build());
  }

  @Test
  public void test() {
    test("Dr. Fujii works at Twitter Inc.", "Dr.", "Fujii", "works", "at", "Twitter", "Inc.");
    test("Mr. Fujii and Mrs. Fujii are married.",
         "Mr.", "Fujii", "and", "Mrs.", "Fujii", "are", "married", ".");
    test("no contracted word.", "no", "contracted", "word", ".");
    test("Mr Fujii and Mrs Fujii are married.",
         "Mr", "Fujii", "and", "Mrs", "Fujii", "are", "married", ".");
    test("Ms. Lin started working at No. 1 social media company at Mon. Jan. 30.",
         "Ms.", "Lin", "started", "working", "at", "No.", "1", "social", "media", "company",
         "at", "Mon.", "Jan.", "30", ".");
  }

  private void test(String text, String... tokens) {
    stream.reset(text);
    assertEquals(Lists.newArrayList(tokens), stream.toStringList());
  }
}
