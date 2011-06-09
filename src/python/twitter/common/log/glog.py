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

import time
import logging

class GlogFormatter(logging.Formatter):
  """
    Format a log in Google style format:
    [DIWEF]mmdd hh:mm:ss.uuuuuu threadid file:line] msg
  """

  LEVEL_MAP = {
    logging.FATAL: 'F',
    logging.ERROR: 'E',
    logging.WARN:  'W',
    logging.INFO:  'I',
    logging.DEBUG: 'D'
  }

  def __init__(self):
    logging.Formatter.__init__(self)

  def format(self, record):
    try:
      level = GlogFormatter.LEVEL_MAP[record.levelno]
    except:
      level = '?'
    date = time.localtime(record.created)
    date_usec = (record.created - int(record.created)) * 1e6
    record_message = '%c%02d%02d %02d:%02d:%02d.%06d %s %s:%d] %s' % (
       level, date.tm_mon, date.tm_mday, date.tm_hour, date.tm_min, date.tm_sec, date_usec,
       record.process if record.process is not None else '?????',
       record.filename,
       record.lineno,
       record.msg)
    record.getMessage = lambda: record_message
    return logging.Formatter.format(self, record)
