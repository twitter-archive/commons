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

import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import com.twitter.common.stats.Stat;

/**
 * HTTP handler that prints all registered variables and their current values.
 *
 * @author William Farner
 */
public class VarsHandler extends TextResponseHandler {

  private static final Function<Stat, String> VAR_PRINTER = new Function<Stat, String>() {
    @Override public String apply(Stat stat) {
      return stat.getName() + " " + stat.read();
    }
  };

  private final Supplier<Iterable<Stat<?>>> statSupplier;

  /**
   * Creates a new handler that will report stats from the provided supplier.
   *
   * @param statSupplier Stats supplier.
   */
  @Inject
  public VarsHandler(Supplier<Iterable<Stat<?>>> statSupplier) {
    this.statSupplier = Preconditions.checkNotNull(statSupplier);
  }

  @Override
  public Iterable<String> getLines(HttpServletRequest request) {
    List<String> lines = Lists.newArrayList(Iterables.transform(statSupplier.get(), VAR_PRINTER));
    Collections.sort(lines);
    return lines;
  }
}
