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

package com.twitter.common.application.modules;

import java.io.File;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.CanRead;
import com.twitter.common.args.constraints.Exists;
import com.twitter.common.args.constraints.IsDirectory;
import com.twitter.common.base.Command;
import com.twitter.common.logging.LogUtil;
import com.twitter.common.logging.RootLogConfig;
import com.twitter.common.logging.RootLogConfig.Configuration;
import com.twitter.common.net.http.handlers.LogPrinter;
import com.twitter.common.stats.StatImpl;
import com.twitter.common.stats.Stats;

/**
 * Binding module for logging-related bindings, such as the log directory.
 *
 * This module uses a single optional command line argument 'log_dir'.  If unset, the logging
 * directory will be auto-discovered via:
 * {@link com.twitter.common.logging.LogUtil#getLogManagerLogDir()}.
 *
 * Bindings provided by this module:
 * <ul>
 *   <li>{@code @Named(LogPrinter.LOG_DIR_KEY) File} - Log directory.
 *   <li>{@code Optional&lt;RootLogConfig.Configuraton&gt;} - If glog is enabled the configuration
 *       used.
 * </ul>
 *
 * Default bindings that may be overridden:
 * <ul>
 *   <li>Log directory: directory where application logs are written.  May be overridden by binding
 *       to: {@code bind(File.class).annotatedWith(Names.named(LogPrinter.LOG_DIR_KEY))}.
 * </ul>
 *
 * @author William Farner
 */
public class LogModule extends AbstractModule {

  private static final Logger LOG = Logger.getLogger(LogModule.class.getName());

  @Exists
  @CanRead
  @IsDirectory
  @CmdLine(name = "log_dir",
           help = "The directory where application logs are written.")
  private static final Arg<File> LOG_DIR = Arg.create(null);

  @CmdLine(name = "use_glog",
           help = "True to use the new glog-based configuration for the root logger.")
  private static final Arg<Boolean> USE_GLOG = Arg.create(true);

  @Override
  protected void configure() {
    // Bind the default log directory.
    bind(File.class).annotatedWith(Names.named(LogPrinter.LOG_DIR_KEY)).toInstance(getLogDir());

    LifecycleModule.bindStartupAction(binder(), ExportLogDir.class);

    Configuration configuration = null;
    if (USE_GLOG.get()) {
      configuration = RootLogConfig.configurationFromFlags();
      configuration.apply();
    }
    bind(new TypeLiteral<Optional<Configuration>>() { })
        .toInstance(Optional.fromNullable(configuration));
  }

  private File getLogDir() {
    File logDir = LOG_DIR.get();
    if (logDir == null) {
      logDir = LogUtil.getLogManagerLogDir();
      LOG.info("From logging properties, parsed log directory " + logDir.getAbsolutePath());
    }
    return logDir;
  }

  public static final class ExportLogDir implements Command {
    private final File logDir;

    @Inject ExportLogDir(@Named(LogPrinter.LOG_DIR_KEY) final File logDir) {
      this.logDir = Preconditions.checkNotNull(logDir);
    }

    @Override public void execute() {
      Stats.exportStatic(new StatImpl<String>("logging_dir") {
        @Override public String read() {
          return logDir.getAbsolutePath();
        }
      });
    }
  }
}
