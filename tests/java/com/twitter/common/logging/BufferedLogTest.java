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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

/**
 * Tests the BufferedLog.
 *
 * @author William Farner
 */
public class BufferedLogTest {

  private static final Predicate<Boolean> RETRY_FILTER = new Predicate<Boolean>() {
    @Override public boolean apply(Boolean value) {
      return !value;
    }
  };
  private static final int BUFFER_SIZE = 5;
  private static final int MAX_BUFFER_SIZE = 10;

  private static final Boolean TRUE = Boolean.TRUE;
  private static final Boolean FALSE = Boolean.FALSE;

  private static final List<String> MESSAGES = Arrays.asList("1", "2", "3", "4", "5");

  private BufferedLog<String, Boolean> bufferedLog;
  private Log<String, Boolean> wrappedLog;

  @Before
  @SuppressWarnings("unchecked") // Due to createMock.
  public void setUp() {
    wrappedLog = createMock(Log.class);

    bufferedLog = BufferedLog.<String, Boolean>builder()
        .buffer(wrappedLog)
        .withRetryFilter(RETRY_FILTER)
        .withChunkLength(BUFFER_SIZE)
        .withMaxBuffer(MAX_BUFFER_SIZE)
        .withFlushInterval(Amount.of(10000, Time.SECONDS))
        .withExecutorService(MoreExecutors.sameThreadExecutor())
        .build();
  }

  @After
  public void runTest() {
    verify(wrappedLog);
    assertThat(bufferedLog.getBacklog(), is(0));
  }

  @Test
  public void testBuffers() {
    expect(wrappedLog.log(MESSAGES)).andReturn(TRUE);

    replay(wrappedLog);
    bufferedLog.log(MESSAGES);
  }

  @Test
  public void testFlush() {
    expect(wrappedLog.log(Arrays.asList("a", "b", "c"))).andReturn(TRUE);

    replay(wrappedLog);
    bufferedLog.log("a");
    bufferedLog.log("b");
    bufferedLog.log("c");
    bufferedLog.flush();
  }

  @Test
  public void testBufferRetries() {
    List<String> bufferAppended = ImmutableList.<String>builder().addAll(MESSAGES).add("6").build();

    expect(wrappedLog.log(MESSAGES)).andReturn(FALSE);
    expect(wrappedLog.log(bufferAppended)).andReturn(TRUE);

    replay(wrappedLog);
    bufferedLog.log(MESSAGES);
    bufferedLog.log("6");
  }

  @Test
  public void testTruncates() {
    expect(wrappedLog.log(MESSAGES)).andReturn(FALSE);
    expect(wrappedLog.log(Arrays.asList("1", "2", "3", "4", "5", "a"))).andReturn(FALSE);
    expect(wrappedLog.log(Arrays.asList("1", "2", "3", "4", "5", "a", "b"))).andReturn(FALSE);
    expect(wrappedLog.log(Arrays.asList("1", "2", "3", "4", "5", "a", "b", "c"))).andReturn(FALSE);
    expect(wrappedLog.log(Arrays.asList("1", "2", "3", "4", "5", "a", "b", "c", "d")))
        .andReturn(FALSE);
    expect(wrappedLog.log(Arrays.asList("1", "2", "3", "4", "5", "a", "b", "c", "d", "e")))
        .andReturn(FALSE);
    expect(wrappedLog.log(Arrays.asList("1", "2", "3", "4", "5", "a", "b", "c", "d", "e", "f")))
        .andReturn(FALSE);
    expect(wrappedLog.log(Arrays.asList("2", "3", "4", "5", "a", "b", "c", "d", "e", "f", "g")))
        .andReturn(FALSE);
    expect(wrappedLog.log(Arrays.asList("3", "4", "5", "a", "b", "c", "d", "e", "f", "g", "h")))
        .andReturn(TRUE);

    replay(wrappedLog);

    bufferedLog.log(MESSAGES);
    bufferedLog.log("a");
    bufferedLog.log("b");
    bufferedLog.log("c");
    bufferedLog.log("d");
    bufferedLog.log("e");
    bufferedLog.log("f");
    bufferedLog.log("g");
    bufferedLog.log("h");
  }
}
