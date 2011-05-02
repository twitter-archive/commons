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

package com.twitter.common.net.http.handlers;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Longs;

import javax.servlet.http.HttpServletRequest;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HTTP request handler that prints information about blocked threads.
 *
 * @author William Farner
 */
public class ContentionPrinter extends TextResponseHandler {
  public ContentionPrinter() {
    ManagementFactory.getThreadMXBean().setThreadContentionMonitoringEnabled(true);
  }

  @Override
  public Iterable<String> getLines(HttpServletRequest request) {
    List<String> lines = Lists.newLinkedList();
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();

    Map<Long, StackTraceElement[]> threadStacks = Maps.newHashMap();
    for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
      threadStacks.put(entry.getKey().getId(), entry.getValue());
    }

    Set<Long> lockOwners = Sets.newHashSet();

    lines.add("Locked threads:");
    for (ThreadInfo t : bean.getThreadInfo(bean.getAllThreadIds())) {
      switch (t.getThreadState()) {
        case BLOCKED:
        case WAITING:
        case TIMED_WAITING:
          lines.addAll(getThreadInfo(t, threadStacks.get(t.getThreadId())));
          if (t.getLockOwnerId() != -1) lockOwners.add(t.getLockOwnerId());
          break;
      }
    }

    if (lockOwners.size() > 0) {
      lines.add("\nLock Owners");
      for (ThreadInfo t : bean.getThreadInfo(Longs.toArray(lockOwners))) {
        lines.addAll(getThreadInfo(t, threadStacks.get(t.getThreadId())));
      }
    }

    return lines;
  }

  private static List<String> getThreadInfo(ThreadInfo t, StackTraceElement[] stack) {
    List<String> lines = Lists.newLinkedList();

    lines.add(String.format("'%s' Id=%d %s",
        t.getThreadName(), t.getThreadId(), t.getThreadState()));
    lines.add("Waiting for lock: " + t.getLockName());
    lines.add("Lock is currently held by thread: " + t.getLockOwnerName());
    lines.add("Wait time: " + t.getBlockedTime() + " ms.");
    for (StackTraceElement s : stack) {
      lines.add(String.format("    " + s.toString()));
    }
    lines.add("\n");

    return lines;
  }
}
