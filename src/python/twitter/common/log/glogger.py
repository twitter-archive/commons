# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

"""
Interface to Glog-stlye formatter.

import twitter.common.log

for __main__:
  log = twitter.common.log.init('my_binary_name')

for library/endpoint:
  log = twitter.common.log.get()

log.info('info baby')
log.debug('debug baby')
log.fatal('oops fatal!')

Will log to my_binary_name.{INFO,WARNING,ERROR,...} into log_dir using the
Google logging format.

See twitter.com.log.options for customizations.
"""

import os
import sys
import time
import logging
import getpass

from glog import GlogFormatter
from .options import LogOptions

class GlogLevelFilter(logging.Filter):
  def __init__(self, levelno):
    self._levelno = levelno
    logging.Filter.__init__(self)

  def filter(self, record):
    if record.levelno == self._levelno:
      return 1
    return 0

def _setup_logging_partitions(handler_class, filename):
  _GLOG_FILTERS = {
    logging.DEBUG: GlogLevelFilter(logging.DEBUG),
    logging.INFO:  GlogLevelFilter(logging.INFO),
    logging.WARN:  GlogLevelFilter(logging.WARN),
    logging.FATAL: GlogLevelFilter(logging.FATAL),
    logging.ERROR: GlogLevelFilter(logging.ERROR)
  }

  logroot = LogOptions.log_dir()
  handlers = []
  for filter_type in _GLOG_FILTERS:
    if LogOptions.disk_log_level() is not None and (
        filter_type >= LogOptions.disk_log_level()):
      logfile = os.path.join(logroot, filename)
      logfile = '.'.join([logfile, getpass.getuser(), logging.getLevelName(filter_type)])
      file_handler = handler_class(logfile)
      file_handler.setFormatter(GlogFormatter())
      file_handler.addFilter(_GLOG_FILTERS[filter_type])
      handlers.append(file_handler)
  return handlers

def init(filebase):
  """
    Set up default logging using:
      {--log_dir}/filebase.{INFO,WARNING,...}
  """
  # set up permissive logger
  root_logger = logging.getLogger()
  root_logger.setLevel(logging.DEBUG)

  # setup INFO...FATAL handlers
  for handler in _setup_logging_partitions(logging.FileHandler, filebase):
    root_logger.addHandler(handler)

  # setting stdout handler
  if LogOptions.stdout_log_level():
    stdout_handler = logging.StreamHandler(sys.stdout)
    stdout_handler.setFormatter(GlogFormatter())
    stdout_handler.setLevel(LogOptions.stdout_log_level())
    root_logger.addHandler(stdout_handler)

  return root_logger

def get():
  """
    Get root logger, synonymous with logging.getLogger()
  """
  return logging.getLogger()
