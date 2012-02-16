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
from twitter.common.log.formatters.base import format_message

class PlainFormatter(logging.Formatter):
  """
    Format a log in a plainer style:
    type] msg
  """
  SCHEME = 'plain'

  LEVEL_MAP = {
    logging.FATAL: 'FATAL',
    logging.ERROR: 'ERROR',
    logging.WARN:  ' WARN',
    logging.INFO:  ' INFO',
    logging.DEBUG: 'DEBUG'
  }

  def __init__(self):
    logging.Formatter.__init__(self)

  def format(self, record):
    try:
      level = PlainFormatter.LEVEL_MAP[record.levelno]
    except:
      level = '?????'
    record_message = '%s] %s' % (level, format_message(record))
    record.getMessage = lambda: record_message
    return logging.Formatter.format(self, record)
