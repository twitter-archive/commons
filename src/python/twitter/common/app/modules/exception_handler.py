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

import sys
import threading
import traceback
import inspect
try:
  from cStringIO import StringIO
except ImportError:
  from StringIO import StringIO
from twitter.common import app, options
try:
  import logging
  from twitter.common import log
  from twitter.common.log.options import LogOptions
  _LOG_MODULE = True
except:
  _LOG_MODULE = False

def log_function(msg):
  if _LOG_MODULE:
    log.error(msg)
  # ensure that at least one message goes to stdout/stderr
  if not _LOG_MODULE or LogOptions.stdout_log_level() > logging.ERROR:
    sys.stderr.write(msg)

class AppExceptionHandler(app.Module):
  """
    An application module that logs or scribes uncaught exceptions.
  """

  OPTIONS = {
    'enable':
      options.Option('--enable_scribe_exception_hook',
          default=False,
          action='store_true',
          dest='twitter_common_internal_scribe_enable',
          help='Enable logging unhandled exceptions to scribe.'),
    'port':
      options.Option('--scribe_exception_port',
          default=1463,
          type='int',
          metavar='PORT',
          dest='twitter_common_internal_scribe_port',
          help='The port to which exceptions should be scribed.'),
    'host':
      options.Option('--scribe_exception_host',
          default='localhost',
          type='string',
          metavar='HOSTNAME',
          dest='twitter_common_internal_scribe_host',
          help='The host to which exceptions should be scribed.'),
    'category':
      options.Option('--scribe_exception_category',
          default='python',
          type='string',
          metavar='CATEGORY',
          dest='twitter_common_internal_scribe_category',
          help='The category to which exceptions should be scribed.')
  }

  def __init__(self):
    app.Module.__init__(self, __name__, description="Scribe exception handler.")

  def setup_function(self):
    sys.excepthook = AppExceptionHandler.handle_error

  def teardown_function(self):
    sys.excepthook = sys.__excepthook__

  @staticmethod
  def synthesize_thread_stacks():
    threads = dict([(th.ident, th) for th in threading.enumerate()])
    ostr = StringIO()
    if len(sys._current_frames()) > 1 or (
        sys._current_frames().values()[0] != inspect.currentframe()):
      # Multi-threaded
      ostr.write('\nAll threads:\n')
      for thread_id, stack in sys._current_frames().items():
        AppExceptionHandler.print_stack(thread_id, threads[thread_id], stack, ostr, indent=2)
    return ostr.getvalue()

  @staticmethod
  def print_stack(thread_id, thread, stack, fh=sys.stderr, indent=0):
    def print_indented(msg):
      print >> fh, '%s%s' % (' '*indent, msg)
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
  def handle_error(exctype, value, tb):
    ostr = StringIO()
    ostr.write('Uncaught exception:\n')
    ostr.write(''.join(traceback.format_exception(exctype, value, tb)))
    ostr.write(AppExceptionHandler.synthesize_thread_stacks())
    log_function(ostr.getvalue())
    if app.get_options().twitter_common_internal_scribe_enable:
      AppExceptionHandler.scribe_error(ostr.getvalue())

  @staticmethod
  def scribe_error(value):
    from scribe import scribe
    from thrift.transport import TTransport, TSocket
    from thrift.protocol import TBinaryProtocol
    options = app.get_options()

    socket = TSocket.TSocket(host=options.twitter_common_internal_scribe_host,
                             port=options.twitter_common_internal_scribe_port)
    transport = TTransport.TFramedTransport(socket)
    protocol = TBinaryProtocol.TBinaryProtocol(
        trans=transport, strictRead=False, strictWrite=False)
    client = scribe.Client(iprot=protocol, oprot=protocol)
    log_entry = scribe.LogEntry(category=options.twitter_common_internal_scribe_category,
        message=value)

    transport.open()
    result = client.Log(messages=[log_entry])
    transport.close()

    if result != scribe.ResultCode.OK:
      log_function('Could not scribe the exception!')
