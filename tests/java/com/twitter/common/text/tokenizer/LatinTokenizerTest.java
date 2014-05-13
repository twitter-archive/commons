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

package com.twitter.common.text.tokenizer;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class LatinTokenizerTest {
  LatinTokenizer tokenizer = new LatinTokenizer.Builder().setKeepPunctuation(false).build();
  LatinTokenizer tokenizerWithPunct = new LatinTokenizer.Builder().setKeepPunctuation(true).build();

  @Test
  public void test() {
    String text = "This is test, and it must pass.";
    testTokenizer(tokenizer, text, "This", "is", "test", "and", "it", "must", "pass");
    testTokenizer(tokenizerWithPunct, text,
        "This", "is", "test", ",", "and", "it", "must", "pass", ".");
  }

  @Test
  public void testUnicodeSupplementaryCharacter() {
    String text = String.format("banana %cpple orange", 0x00010400);
    testTokenizer(tokenizer, text, "banana", String.format("%cpple", 0x00010400), "orange");
  }

  @Test
  public void testSingleCharacter() {
    testTokenizer(tokenizer, "", new String[0]);
    testTokenizer(tokenizer, " ", new String[0]);
    testTokenizer(tokenizer, "a", "a");
    testTokenizer(tokenizer, "\n", new String[0]);
    testTokenizer(tokenizerWithPunct, "\n", "\n");
    testTokenizer(tokenizer, ".", new String[0]);
    testTokenizer(tokenizerWithPunct, ".", ".");
    testTokenizer(tokenizer, "#", new String[0]);
    testTokenizer(tokenizerWithPunct, "#", "#");
  }

  private void testTokenizer(LatinTokenizer tokenizer, String test, String... expected) {
    tokenizer.reset(test);
    List<String> tokens = tokenizer.toStringList();
    assertEquals(expected.length, tokens.size());
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], tokens.get(i).toString());
    }
  }
}
