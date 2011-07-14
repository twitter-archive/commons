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

import logging

from initialize import init

try:
  from twitter.common import app
  app.on_initialization(
    lambda: init(app.name()),
    description="Logging subsystem.")
except ImportError:
  # Do not require twitter.common.app
  pass

debug = logging.debug
info = logging.info
warning = logging.warning
error = logging.error
fatal = logging.fatal

__all__ = [
  'debug',
  'info',
  'warning',
  'error',
  'fatal',

  # only if you're not using app directly.
  'init',

  # ditto
  'formatters'
]
