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

import java.io.IOException;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.LoggingMXBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;

import org.antlr.stringtemplate.StringTemplate;
import org.apache.commons.lang.StringUtils;

import com.twitter.common.base.Closure;

/**
 * Servlet that allows for dynamic adjustment of the logging configuration.
 *
 * @author William Farner
 */
public class LogConfig extends StringTemplateServlet {
  private static final List<String> LOG_LEVELS = Lists.newArrayList(
      Level.SEVERE.getName(),
      Level.WARNING.getName(),
      Level.INFO.getName(),
      Level.CONFIG.getName(),
      Level.FINE.getName(),
      Level.FINER.getName(),
      Level.FINEST.getName(),
      "INHERIT" // Display value for a null level, the logger inherits from its ancestor.
  );

  @Inject
  public LogConfig(@CacheTemplates boolean cacheTemplates) {
    super("logconfig", cacheTemplates);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    displayPage(req, resp, true);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    displayPage(req, resp, false);
  }

  protected void displayPage(final HttpServletRequest req, HttpServletResponse resp,
      final boolean posted) throws ServletException, IOException {
    writeTemplate(resp, new Closure<StringTemplate>() {
      @Override public void execute(StringTemplate stringTemplate) {
        LoggingMXBean logBean = LogManager.getLoggingMXBean();

        if (posted) {
          String loggerName = req.getParameter("logger");
          String loggerLevel = req.getParameter("level");
          if (loggerName != null && loggerLevel != null) {
            Logger logger = Logger.getLogger(loggerName);
            Level newLevel = loggerLevel.equals("INHERIT") ? null : Level.parse(loggerLevel);
            logger.setLevel(newLevel);
            if (newLevel != null) {
              maybeAdjustHandlerLevels(logger, newLevel);
            }

            stringTemplate.setAttribute("configChange",
                String.format("%s level changed to %s", loggerName, loggerLevel));
          }
        }

        List<LoggerConfig> loggerConfigs = Lists.newArrayList();
        for (String logger : Ordering.natural().immutableSortedCopy(logBean.getLoggerNames())) {
          loggerConfigs.add(new LoggerConfig(logger, logBean.getLoggerLevel(logger)));
        }

        stringTemplate.setAttribute("loggers", loggerConfigs);
        stringTemplate.setAttribute("levels", LOG_LEVELS);
      }
    });
  }

  private void maybeAdjustHandlerLevels(Logger logger, Level newLevel) {
    do {
      for (Handler handler : logger.getHandlers()) {
        Level handlerLevel = handler.getLevel();
        if (newLevel.intValue() < handlerLevel.intValue()) {
          handler.setLevel(newLevel);
        }
      }
    } while (logger.getUseParentHandlers() && (logger = logger.getParent()) != null);
  }

  private class LoggerConfig {
    private final String name;
    private final String level;

    public LoggerConfig(String name, String level) {
      this.name = name;
      this.level = StringUtils.isBlank(level) ? "INHERIT" : level;
    }

    public String getName() {
      return name;
    }

    public String getLevel() {
      return level;
    }
  }
}
