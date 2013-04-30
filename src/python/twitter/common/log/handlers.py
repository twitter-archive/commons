# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

from logging import Handler

try:
  from scribe import scribe
  from thrift.protocol import TBinaryProtocol
  from thrift.transport import TTransport, TSocket
  _SCRIBE_PRESENT = True
except ImportError:
  _SCRIBE_PRESENT = False


class ScribeHandler(Handler):
  """logging.Handler interface for Scribe."""
  class ScribeHandlerException(Exception):
    pass

  def __init__(self, *args, **kwargs):
    """logging.Handler interface for Scribe.

    Params:
    buffer: If True, buffer messages when scribe is unavailable. If False, drop on floor.
    category: Scribe category for logging events.
    host: Scribe host.
    port: Scribe port.
    """
    if not _SCRIBE_PRESENT:
      raise self.ScribeHandlerException(
        "Could not initialize ScribeHandler: Scribe modules not present.")
    self._buffer_enabled = kwargs.pop("buffer")
    self._category = kwargs.pop("category")
    self._client = None
    self._host = kwargs.pop("host")
    self._log_buffer = []
    self._port = kwargs.pop("port")
    self._transport = None
    Handler.__init__(self, *args, **kwargs)

  @property
  def messages_pending(self):
    """Return True if there are messages in the buffer."""
    return bool(self._log_buffer)

  @property
  def client(self):
    """Scribe client object."""
    if not self._client:
      protocol = TBinaryProtocol.TBinaryProtocol(trans=self.transport,
                                                 strictRead=False,
                                                 strictWrite=False)
      self._client = scribe.Client(iprot=protocol, oprot=protocol)
    return self._client

  @property
  def transport(self):
    """Scribe transport object."""
    if not self._transport:
      socket = TSocket.TSocket(host=self._host, port=self._port)
      self._transport = TTransport.TFramedTransport(socket)
    return self._transport

  def close(self):
    """Flushes any remaining messages in the queue."""
    if self.messages_pending:
      try:
        self.scribe_write(self._log_buffer)
      except self.ScribeHandlerException:
        pass
    Handler.close(self)

  def emit(self, record):
    """Emit a record via Scribe."""
    fmt_record = self.format(record)
    self._log_buffer.append(scribe.LogEntry(category=self._category, message=fmt_record))
    try:
      self.scribe_write(self._log_buffer)
    except self.ScribeHandlerException:
      if not self._buffer_enabled:
        self._log_buffer = []
    else:
      self._log_buffer = []

  def scribe_write(self, messages):
    """Sends a list of messages to scribe.

    Params:
    messages: List of scribe.LogEntry objects.

    Raises:
    ScribeHandlerException on timeouts and connection errors.
    """
    try:
      self.transport.open()
      result = self.client.Log(messages)
      if result != scribe.ResultCode.OK:
        raise self.ScribeHandlerException('Scribe message submission failed')
    except TTransport.TTransportException as err:
      raise self.ScribeHandlerException('Could not connect to scribe host=%s:%s error=%s'
                                        % (self._host, self._port, err))
    finally:
      self.transport.close()
