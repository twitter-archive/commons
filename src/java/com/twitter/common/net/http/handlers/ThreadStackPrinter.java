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

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * HTTP handler to print the stacks of all live threads.
 *
 * @author William Farner
 */
public class ThreadStackPrinter extends TextResponseHandler {
  @Override
  public Iterable<String> getLines(HttpServletRequest request) {
    List<String> lines = Lists.newLinkedList();
    for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
      Thread t = entry.getKey();
      lines.add(String.format("Name: %s\nState: %s\nDaemon: %s\nID: %d",
          t.getName(), t.getState(), t.isDaemon(), t.getId()));
      for (StackTraceElement s : entry.getValue()) {
        lines.add("    " + s.toString());
      }
    }
    return lines;
  }
}
