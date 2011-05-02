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
package com.twitter.common.checkstyle;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AutomaticBean;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Filter;
import com.puppycrawl.tools.checkstyle.filters.SuppressionsLoader;

/**
 * This filter accepts AuditEvents according to file, check, line, and column, as specified in one
 * or more suppression files.
 *
 * @author John Sirois
 */
public class SplitSuppressionFilter extends AutomaticBean implements Filter {

  /**
   * A filter that delegates acceptance of audit events to a list of filters, failing fast at the
   * first filter to reject the event.
   */
  private static final class CompoundFilter implements Filter {
    private final Iterable<? extends Filter> filters;

    CompoundFilter(Iterable<Filter> filters) {
      this.filters = Preconditions.checkNotNull(filters);
    }

    @Override
    public boolean accept(AuditEvent auditEvent) {
      for (Filter filter : filters) {
        if (!filter.accept(auditEvent)) {
          return false;
        }
      }
      return true;
    }
  }

  private Filter suppressions;

  /**
   * Loads the suppressions defined in the list of files given.
   *
   * @param files Names of the suppressions files.
   * @throws CheckstyleException if there is an error loading any of the suppression files.
   */
  public void setFiles(String[] files) throws CheckstyleException {
    ImmutableList.Builder<Filter> filters = ImmutableList.builder();
    for (String file : files) {
      filters.add(SuppressionsLoader.loadSuppressions(file));
    }
    this.suppressions = new CompoundFilter(filters.build());
  }

  @Override
  public boolean accept(AuditEvent auditEvent) {
    return suppressions.accept(auditEvent);
  }
}
