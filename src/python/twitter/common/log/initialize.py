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

if not using twitter.common.app:
  for __main__:
    log = twitter.common.log.init('my_binary_name')
otherwise init will be called automatically on app.main()

for library/endpoint:
  from twitter.common import log

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

from formatters import glog, plain
from .options import LogOptions

class GenericFilter(logging.Filter):
  def __init__(self, levelfn=lambda record_level: True):
    self._levelfn = levelfn
    logging.Filter.__init__(self)

  def filter(self, record):
    if self._levelfn(record.levelno):
      return 1
    return 0

class ProxyFormatter(logging.Formatter):
  class UnknownSchemeException(Exception): pass

  _SCHEME_TO_FORMATTER = {
    glog.GlogFormatter.SCHEME: glog.GlogFormatter(),
    plain.PlainFormatter.SCHEME: plain.PlainFormatter()
  }

  def __init__(self, scheme_fn):
    logging.Formatter.__init__(self)
    self._scheme_fn = scheme_fn

  def format(self, record):
    scheme = self._scheme_fn()
    if scheme not in ProxyFormatter._SCHEME_TO_FORMATTER:
      raise ProxyFormatter.UnknownSchemeException("Unknown logging scheme: %s" % scheme)
    return ProxyFormatter._SCHEME_TO_FORMATTER[scheme].format(record)

_FILTER_TYPES = [
  logging.DEBUG,
  logging.INFO,
  logging.WARN,
  logging.ERROR,
  logging.FATAL
]

def _setup_disk_logging(filename):
  handlers = []
  logroot = LogOptions.log_dir()

  def gen_filter(level):
    return GenericFilter(
      lambda record_level: record_level == level and level >= LogOptions.disk_log_level())

  for filter_type in _FILTER_TYPES:
    formatter = ProxyFormatter(LogOptions.disk_log_scheme)
    filter = gen_filter(filter_type)
    logfile = os.path.join(logroot, filename)
    logfile = '.'.join([logfile, getpass.getuser(), logging.getLevelName(filter_type)])
    file_handler = logging.FileHandler(logfile)
    file_handler.setFormatter(formatter)
    file_handler.addFilter(filter)
    handlers.append(file_handler)
  return handlers

def _setup_stdout_logging():
  filter = GenericFilter(lambda r_l: r_l >= LogOptions.stdout_log_level())
  formatter = ProxyFormatter(LogOptions.stdout_log_scheme)
  stdout_handler = logging.StreamHandler(sys.stdout)
  stdout_handler.setFormatter(formatter)
  stdout_handler.addFilter(filter)
  return [stdout_handler]

def init(filebase):
  """
    Set up default logging using:
      {--log_dir}/filebase.{INFO,WARNING,...}
  """
  # set up permissive logger
  root_logger = logging.getLogger()
  root_logger.setLevel(logging.DEBUG)

  # setup INFO...FATAL handlers
  for handler in _setup_disk_logging(filebase):
    root_logger.addHandler(handler)
  for handler in _setup_stdout_logging():
    root_logger.addHandler(handler)
  return root_logger
