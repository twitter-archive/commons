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

import socket
import ssl

from thrift.transport.TSSLSocket import TSSLSocket
from thrift.transport.TTransport import TTransportException


class DelayedHandshakeTSSLSocket(TSSLSocket):
  """Monkeypatched TSSLSocket that allows delaying of the initial SSL handshake.

  The purpose of this DelayedHandshakeTSSLSocket is to allow for intermixing other
  transport-layer protocols such as SOCKS.

  Behaves the same as TSSLSocket except it accepts the delay_handshake
  keyword argument.  This defers the SSL handshake until *after* connect.
  """

  def __init__(self, *args, **kw):
    # Curse 2.6.x + PEP-3102
    self.__delay_handshake = kw.pop('delay_handshake', False)
    self.__socket_factory = kw.pop('socket_factory', socket)
    if 'unix_socket' in kw:
      raise ValueError('%s does not support unix_sockets!' % self.__class__.__name__)
    TSSLSocket.__init__(self, *args, **kw)  # thrift does not support super()

  def __do_wrap(self, handshake_on_connect=False):
    # TODO(wickman) Thrift 0.9.1 supports keyfile and certfile.  File a
    # ticket to get delay_handshake added to the Thrift core libs.
    self.handle = ssl.wrap_socket(
        self.handle,
        ssl_version=self.SSL_VERSION,
        do_handshake_on_connect=handshake_on_connect,
        ca_certs=self.ca_certs,
        cert_reqs=self.cert_reqs)

  def open(self):
    try:
      resolved = self._resolveAddr()
      for offset, res in enumerate(resolved):
        sock_family, sock_type, _, _, ip_port = res[0:5]
        self.handle = self.__socket_factory.socket(sock_family, sock_type)
        self.handle.settimeout(self._timeout)
        reraise = (offset == len(resolved) - 1)

        if not self.__delay_handshake:
          self.__do_wrap(handshake_on_connect=True)

        try:
          self.handle.connect(ip_port)
        except self.__socket_factory.error:
          if reraise:
            raise

        if self.__delay_handshake:
          self.__do_wrap(handshake_on_connect=False)
          self.handle.do_handshake()

    except self.__socket_factory.error as e:
      message = 'Could not connect to %s:%d: %s' % (self.host, self.port, e)
      raise TTransportException(type=TTransportException.NOT_OPEN, message=message)

    if self.validate:
      self._validate_cert()
