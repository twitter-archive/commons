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
from twitter.common import app, options
from twitter.common.exceptions import BasicExceptionHandler

from scribe import scribe
from thrift.transport import TTransport, TSocket
from thrift.protocol import TBinaryProtocol

class AppScribeExceptionHandler(app.Module):
  """
    An application module that logs or scribes uncaught exceptions.
  """

  OPTIONS = {
    'port':
      options.Option('--scribe_exception_port',
          default=1463,
          type='int',
          metavar='PORT',
          dest='twitter_common_scribe_port',
          help='The port on which scribe aggregator listens.'),
    'host':
      options.Option('--scribe_exception_host',
          default='localhost',
          type='string',
          metavar='HOSTNAME',
          dest='twitter_common_scribe_host',
          help='The host to which scribe exceptions should be written.'),
    'category':
      options.Option('--scribe_exception_category',
          default='python_default',
          type='string',
          metavar='CATEGORY',
          dest='twitter_common_scribe_category',
          help='The scribe category into which we write exceptions.')
  }


  def __init__(self):
    app.Module.__init__(self, __name__, description="twitter.common.log handler.")

  def setup_function(self):
    self._builtin_hook = sys.excepthook
    def forwarding_handler(*args, **kw):
      AppScribeExceptionHandler.scribe_error(*args, **kw)
      self._builtin_hook(*args, **kw)
    sys.excepthook = forwarding_handler

  def teardown_function(self):
    sys.excepthook = getattr(self, '_builtin_hook', sys.__excepthook__)

  @staticmethod
  def log_error(msg):
    try:
      from twitter.common import log
      log.error(msg)
    except ImportError:
      sys.stderr.write(msg + '\n')

  @staticmethod
  def scribe_error(*args, **kw):
    options = app.get_options()
    socket = TSocket.TSocket(host=options.twitter_common_scribe_host,
                             port=options.twitter_common_scribe_port)
    transport = TTransport.TFramedTransport(socket)
    protocol = TBinaryProtocol.TBinaryProtocol(trans=transport, strictRead=False, strictWrite=False)
    client = scribe.Client(iprot=protocol, oprot=protocol)
    value = BasicExceptionHandler.format(*args, **kw)
    log_entry = scribe.LogEntry(category=options.twitter_common_scribe_category,
      message=value)

    try:
      transport.open()
      result = client.Log(messages=[log_entry])
      transport.close()
      if result != scribe.ResultCode.OK:
        AppScribeExceptionHandler.log_error('Failed to scribe exception!')
    except TTransport.TTransportException:
      AppScribeExceptionHandler.log_error('Could not connect to scribe!')
