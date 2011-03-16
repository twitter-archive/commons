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
