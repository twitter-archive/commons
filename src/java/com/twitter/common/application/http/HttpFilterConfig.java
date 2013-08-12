package com.twitter.common.application.http;

import javax.servlet.Filter;

import com.twitter.common.base.MorePreconditions;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Configuration tuple for an HTTP filter.
 */
public class HttpFilterConfig {
  public final Class<? extends Filter> filterClass;
  public final String pathSpec;

  /**
   * Creates a new filter configuration.
   *
   * @param filterClass Filter class.
   * @param pathSpec Path spec that the filter should match.
   */
  public HttpFilterConfig(Class<? extends Filter> filterClass, String pathSpec) {
    this.pathSpec = MorePreconditions.checkNotBlank(pathSpec);
    this.filterClass = checkNotNull(filterClass);
  }
}
