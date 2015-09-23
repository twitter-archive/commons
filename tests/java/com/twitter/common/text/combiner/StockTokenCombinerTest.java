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

import com.google.common.collect.ImmutableList;

import com.twitter.common.text.token.TwitterTokenStream;
import com.twitter.common.text.tokenizer.LatinTokenizer;

public class StockTokenCombinerTest {
  private TwitterTokenStream stream;

  @Before
  public void setup() {
    stream = new StockTokenCombiner(
        new LatinTokenizer.Builder().setKeepPunctuation(true).build());
  }

  @Test
  public void test() {
    stream.reset("Check out Apple stock: $APPL. and $IN.  But not $dlfsdfsdf. $C is ok. $ is right out.");

    assertEquals(ImmutableList.of("Check", "out", "Apple", "stock", ":", "$APPL", ".",
        "and", "$IN", ".", "But", "not", "$", "dlfsdfsdf", ".", "$C", "is", "ok", ".",
        "$", "is", "right", "out", "."), stream.toStringList());
  }

  @Test
  public void testSymbolWithDotOrUnderscore() {
    stream.reset("Test $APPL.X or $GOOG_Y");

    assertEquals(ImmutableList.of("Test", "$APPL.X", "or", "$GOOG_Y"), stream.toStringList());
  }
}
