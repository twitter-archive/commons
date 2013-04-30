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

# TODO Backend support needs to be broken out into modules, a'la twitter.common.app.
__author__ = 'Brian Wickman'

import logging

from .initialize import (
  init,
  teardown_disk_logging,
  teardown_stderr_logging)
from .tracer import Tracer

try:
  from twitter.common import app
  from twitter.common.log.options import LogOptions

  class LoggingSubsystem(app.Module):
    def __init__(self):
      app.Module.__init__(self, __name__, description="Logging subsystem.")
    def setup_function(self):
      if not LogOptions._is_disk_logging_required():
        init()
      else:
        init(app.name())

  app.register_module(LoggingSubsystem())
except ImportError:
  # Do not require twitter.common.app
  pass

debug = logging.debug
info = logging.info
warning = logging.warning
warn = logging.warning
error = logging.error
fatal = logging.fatal
log = logging.log
logger = logging.getLogger

DEBUG = logging.DEBUG
INFO = logging.INFO
WARNING = logging.WARNING
WARN = logging.WARN
ERROR = logging.ERROR
FATAL = logging.FATAL

__all__ = (
  # directives
  'debug',
  'info',
  'warning',
  'warn', # alias
  'error',
  'fatal',
  'log',
  'logger',

  # levels
  'DEBUG',
  'INFO',
  'WARNING',
  'WARN',
  'ERROR',
  'FATAL',

  # only if you're not using app directly.
  'init',
  'teardown_stderr_logging',
  'teardown_disk_logging',

  # ditto
  'formatters',

  # other things
  'Tracer'
)
