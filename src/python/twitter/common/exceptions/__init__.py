# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

from __future__ import print_function

import inspect
import sys
import threading
import traceback

from twitter.common.decorators import identify_thread
from twitter.common.lang import Compatibility

try:
  import logging
  from twitter.common import log
  from twitter.common.log.options import LogOptions
  _LOG_MODULE = True
except:
  _LOG_MODULE = False


__all__ = (
  'BasicExceptionHandler',
  'ExceptionalThread',
)


def log_function(msg):
  if _LOG_MODULE:
    log.error(msg)
  # ensure that at least one message goes to stdout/stderr
  if not _LOG_MODULE or LogOptions.stderr_log_level() > logging.ERROR:
    sys.stderr.write(msg)


class BasicExceptionHandler(object):
  """
    Threaded stack trace exception handling that leverages
    twitter.common.log if it is available.

    To use:
      from twitter.common.exceptions import BasicExceptionHandler
      BasicExceptionHandler.install()

    Then raise away!
  """

  @staticmethod
  def print_stack(thread_id, thread, stack, fh=sys.stderr, indent=0):
    def print_indented(msg):
      print('%s%s' % (' '*indent, msg), file=fh)
    print_indented('Thread%s: %s (%s, %d)' % (
        ' (daemon)' if thread.daemon else '',
        thread.__class__.__name__,
        thread.name,
        thread_id))
    for filename, lineno, name, line in traceback.extract_stack(stack):
      print_indented('  File: "%s", line %d, in %s' % (filename, lineno, name))
      if line:
        print_indented('    %s' % line.strip())
    print_indented('')

  @staticmethod
  def synthesize_thread_stacks():
    threads = dict([(th.ident, th) for th in threading.enumerate()])
    ostr = Compatibility.StringIO()
    # _current_frames not yet implemented on pypy and not guaranteed anywhere but
    # cpython in practice.
    if hasattr(sys, '_current_frames') and (len(sys._current_frames()) > 1 or
        sys._current_frames().values()[0] != inspect.currentframe()):
      # Multi-threaded
      ostr.write('\nAll threads:\n')
      for thread_id, stack in sys._current_frames().items():
        BasicExceptionHandler.print_stack(thread_id, threads[thread_id], stack, ostr, indent=2)
    return ostr.getvalue()

  @staticmethod
  def format(exctype, value, tb):
    ostr = Compatibility.StringIO()
    ostr.write('Uncaught exception:\n')
    ostr.write(''.join(traceback.format_exception(exctype, value, tb)))
    ostr.write(BasicExceptionHandler.synthesize_thread_stacks())
    return ostr.getvalue()

  @staticmethod
  def handle_error(exctype, value, tb):
    log_function(BasicExceptionHandler.format(exctype, value, tb))

  @staticmethod
  def install():
    sys.excepthook = BasicExceptionHandler.handle_error

  @staticmethod
  def uninstall():
    sys.excepthook = sys.__excepthook__


class ExceptionalThread(threading.Thread):
  """Pattern from http://bugs.python.org/issue1230540

     To instantiate a thread that can propagate exceptions properly, extend
     from ExceptionalThread instead of Thread.
  """

  def __init__(self, *args, **kw):
    super(ExceptionalThread, self).__init__(*args, **kw)
    self.__real_run, self.run = self.run, self._excepting_run

  @identify_thread
  def _excepting_run(self, *args, **kw):
    try:
      self.__real_run(*args, **kw)
    except (KeyboardInterrupt, SystemExit):
      raise
    except:
      sys.excepthook(*sys.exc_info())
