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
import com.twitter.common.stats.Stat;
import com.twitter.common.stats.Stats;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;

/**
 * HTTP handler that prints all registered variables and their current values.
 *
 * @author William Farner
 */
public class VarsHandler extends TextResponseHandler {

  @Override
  public Iterable<String> getLines(HttpServletRequest request) {
    List<String> lines = Lists.newArrayList();
    for (Stat var : Stats.getVariables()) {
      lines.add(var.getName() + " " + var.read());
    }

    Collections.sort(lines);

    return lines;
  }
}
