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

package com.twitter.common.util;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;

import com.twitter.common.base.MorePreconditions;

/**
 * Handles loading of a build properties file, and provides keys to look up known values in the
 * properties.
 */
public class BuildInfo {

  private static final Logger LOG = Logger.getLogger(BuildInfo.class.getName());

  private static final String DEFAULT_BUILD_PROPERTIES_PATH = "build.properties";

  private final String resourcePath;

  private Properties properties = null;

  /**
   * Creates a build info container that will use the default properties file path.
   */
  public BuildInfo() {
    this(DEFAULT_BUILD_PROPERTIES_PATH);
  }

  /**
   * Creates a build info container, reading from the given path.
   *
   * @param resourcePath The resource path to read build properties from.
   */
  public BuildInfo(String resourcePath) {
    this.resourcePath = MorePreconditions.checkNotBlank(resourcePath);
  }

  @VisibleForTesting
  public BuildInfo(Properties properties) {
    this.resourcePath = null;
    this.properties = properties;
  }

  private void fetchProperties() {
    properties = new Properties();
    LOG.info("Fetching build properties from " + resourcePath);
    InputStream in = ClassLoader.getSystemResourceAsStream(resourcePath);
    if (in == null) {
      LOG.warning("Failed to fetch build properties from " + resourcePath);
      return;
    }

    try {
      properties.load(in);
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Failed to load properties file " + resourcePath, e);
    }
  }

  /**
   * Fetches the properties stored in the resource location.
   *
   * @return The loaded properties, or a default properties object if there was a problem loading
   *    the specified properties resource.
   */
  public Properties getProperties() {
    if (properties == null) fetchProperties();
    return properties;
  }

  /**
   * Values of keys that are expected to exist in the loaded properties file.
   */
  public enum Key {
    PATH("build.path"),
    USER("build.user.name"),
    MACHINE("build.machine"),
    DATE("build.date"),
    TIME("build.time"),
    TIMESTAMP("build.timestamp"),
    GIT_TAG("build.git.tag"),
    GIT_REVISION("build.git.revision"),
    GIT_REVISION_NUMBER("build.git.revision.number"),
    GIT_BRANCHNAME("build.git.branchname");

    public final String value;
    private Key(String value) {
      this.value = value;
    }
  }
}
