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
Glog log system global options.

Exports module-level options such as --log_dir and --stdout_log_level, but may be
overridden locally before calling log.init().
"""

import logging

from twitter.common import options

class LogOptionsException(Exception): pass

class LogOptions:
  _LOG_LEVELS = {
    'DEBUG': logging.DEBUG,
    'INFO':  logging.INFO,
    'WARN':  logging.WARN,
    'FATAL': logging.FATAL,
    'ERROR': logging.ERROR,
    'NONE': None
  }

  _STDOUT_LOG_LEVEL = None
  _DISK_LOG_LEVEL = None
  _LOG_DIR = None

  @staticmethod
  def set_stdout_log_level(log_level):
    """
      Set the log level for stdout. It must be one of:
        'NONE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'
    """
    if log_level in LogOptions._LOG_LEVELS:
      LogOptions._STDOUT_LOG_LEVEL = LogOptions._LOG_LEVELS[log_level]
    else:
      raise LogOptionsException("Invalid log level: %s" % log_level)

  @staticmethod
  def stdout_log_level():
    """
      Get the current stdout_log_level (in logging units specified by logging module.)
    """
    if LogOptions._STDOUT_LOG_LEVEL is None:
      LogOptions.set_stdout_log_level(options.values().glog_log_to_stdout)
    return LogOptions._STDOUT_LOG_LEVEL

  @staticmethod
  def set_disk_log_level(log_level):
    """
      Set the log level for disk. It must be one of:
        'NONE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'
    """
    if log_level in LogOptions._LOG_LEVELS:
      LogOptions._DISK_LOG_LEVEL = LogOptions._LOG_LEVELS[log_level]
    else:
      raise LogOptionsException("Invalid log level: %s" % log_level)

  @staticmethod
  def disk_log_level():
    """
      Get the current disk_log_level (in logging units specified by logging module.)
    """
    if LogOptions._DISK_LOG_LEVEL is None:
      LogOptions.set_stdout_log_level(options.values().glog_log_to_disk)
    return LogOptions._DISK_LOG_LEVEL

  @staticmethod
  def set_log_dir(dir):
    """
      Set the logging dir for disk logging.  Must be called before log.init() for
      changes to take effect.
    """
    LogOptions._LOG_DIR = dir

  @staticmethod
  def log_dir():
    """
      Get the current directory into which logs will be written.
    """
    if LogOptions._LOG_DIR is None:
      LogOptions._LOG_DIR = options.values().glog_log_dir
    return LogOptions._LOG_DIR

options.add('--log_to_stdout',
            type='choice',
            choices=LogOptions._LOG_LEVELS.keys(),
            default='NONE',
            metavar='LEVEL',
            dest='glog_log_to_stdout',
            help="The level at which to log to stdout [default: %default], "
                 "can be one of: DEBUG INFO WARN ERROR FATAL NONE")

options.add('--log_to_disk',
            type='choice',
            choices=LogOptions._LOG_LEVELS.keys(),
            default='INFO',
            metavar='LEVEL',
            dest='glog_log_to_disk',
            help="The level at which to log to disk [default: %default], "
                 "can be one of: DEBUG INFO WARN ERROR FATAL NONE")

options.add('--log_dir',
            type='string',
            default='/var/tmp',
            metavar='DIR',
            dest='glog_log_dir',
            help="The directory into which log files will be generated [default: %default].")
