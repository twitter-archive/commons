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

package com.twitter.common.util.logging;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

/**
 * A custom java.util.logging configuration class that loads the logging configuration from a
 * properties file resource (as opposed to a file as natively supported by LogManager via
 * java.util.logging.config.file).  By default this configurator will look for the resource at
 * /logging.properties but the resource path can be overridden by setting the system property with
 * key {@link #LOGGING_PROPERTIES_RESOURCE_PATH java.util.logging.config.resource}.  To install this
 * configurator you must specify the following system property:
 * java.util.logging.config.class=com.twitter.common.util.logging.ResourceLoggingConfigurator
 *
 * @author John Sirois
 */
public class ResourceLoggingConfigurator {

  /**
   * A system property that controls where ResourceLoggingConfigurator looks for the logging
   * configuration on the process classpath.
   */
  public static final String LOGGING_PROPERTIES_RESOURCE_PATH = "java.util.logging.config.resource";

  public ResourceLoggingConfigurator() throws IOException {
    String loggingPropertiesResourcePath =
        System.getProperty(LOGGING_PROPERTIES_RESOURCE_PATH, "/logging.properties");
    InputStream loggingConfig = getClass().getResourceAsStream(loggingPropertiesResourcePath);
    Preconditions.checkNotNull(loggingConfig,
        "Could not locate logging config file at resource path: %s", loggingPropertiesResourcePath);
    LogManager.getLogManager().readConfiguration(loggingConfig);
  }
}
