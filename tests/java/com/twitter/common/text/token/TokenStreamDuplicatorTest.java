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

package com.twitter.common.text.token;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import com.twitter.common.text.DefaultTextTokenizer;
import com.twitter.common.text.filter.TokenTypeFilter;
import com.twitter.common.text.filter.TokenTypeFilter.Mode;
import com.twitter.common.text.token.attribute.TokenType;

public class TokenStreamDuplicatorTest {
  DefaultTextTokenizer tokenizer = new DefaultTextTokenizer.Builder().setKeepPunctuation(true).build();
  TwitterTokenStream tokenOnly;
  TwitterTokenStream punctOnly;

  @Before
  public void setUp() {
    TwitterTokenStream stream = tokenizer.getDefaultTokenStream();
    TokenStreamDuplicator original = new TokenStreamDuplicator(stream);

    tokenOnly = new TokenTypeFilter.Builder(original).setMode(Mode.ACCEPT).setTypesToFilter(TokenType.TOKEN).build();
    punctOnly = new TokenTypeFilter.Builder(original.duplicate()).setMode(Mode.ACCEPT).setTypesToFilter(TokenType.PUNCTUATION).build();
  }

  @Test
  public void testDuplicate() {
    tokenOnly.reset("This stream has the original tokenizer, so this must be used.");
    punctOnly.reset("This stream has a duplicate, so this is ignored.");
    assertEquals(ImmutableList.of("This", "stream", "has", "the", "original", "tokenizer", "so", "this", "must", "be", "used"), tokenOnly.toStringList());
    assertEquals(ImmutableList.of(",", "."), punctOnly.toStringList());
  }

  @Test
  public void testDuplicateAndAggregate() {
    TwitterTokenStream aggregate = TokenStreamAggregator.of(tokenOnly, punctOnly);

    aggregate.reset("a b, c d e.");
    assertEquals(ImmutableList.of("a", "b", "c", "d", "e", ",", "."), aggregate.toStringList());
  }
}
