"""
  A SASL Thrift transport based upon the pure-sasl library, both implemented by
  @tylhobbs and adapted for twitter.common.  See:

    https://issues.apache.org/jira/browse/THRIFT-1719
    https://issues.apache.org/jira/secure/attachment/12548462/1719-python-sasl.txt
"""

from struct import pack, unpack

from twitter.common.lang import Compatibility
StringIO = Compatibility.BytesIO

from puresasl.client import SASLClient
from thrift.transport.TTransport import (
    CReadableTransport,
    TTransportBase,
    TTransportException)


class TSaslClientTransport(TTransportBase, CReadableTransport):
  """
  A SASL transport based on the pure-sasl library:
      https://github.com/thobbs/pure-sasl
  """

  START = 1
  OK = 2
  BAD = 3
  ERROR = 4
  COMPLETE = 5

  def __init__(self, transport, host, service, mechanism='GSSAPI',
      **sasl_kwargs):
    """
    transport: an underlying transport to use, typically just a TSocket
    host: the name of the server, from a SASL perspective
    service: the name of the server's service, from a SASL perspective
    mechanism: the name of the preferred mechanism to use

    All other kwargs will be passed to the puresasl.client.SASLClient
    constructor.
    """
    self.transport = transport
    self.sasl = SASLClient(host, service, mechanism, **sasl_kwargs)
    self.__wbuf = StringIO()
    self.__rbuf = StringIO()

  def open(self):
    if not self.transport.isOpen():
      self.transport.open()

    self.send_sasl_msg(self.START, self.sasl.mechanism)
    self.send_sasl_msg(self.OK, self.sasl.process() or '')

    while True:
      status, challenge = self.recv_sasl_msg()
      if status == self.OK:
        self.send_sasl_msg(self.OK, self.sasl.process(challenge) or '')
      elif status == self.COMPLETE:
        if not self.sasl.complete:
          raise TTransportException("The server erroneously indicated "
              "that SASL negotiation was complete")
        else:
          break
      else:
        raise TTransportException("Bad SASL negotiation status: %d (%s)"
            % (status, challenge))

  def send_sasl_msg(self, status, body):
    if body is None:
      body = ''
    header = pack(">BI", status, len(body))
    self.transport.write(header + body)
    self.transport.flush()

  def recv_sasl_msg(self):
    header = self.transport.readAll(5)
    status, length = unpack(">BI", header)
    if length > 0:
      payload = self.transport.readAll(length)
    else:
      payload = ""
    return status, payload

  def write(self, data):
    self.__wbuf.write(data)

  def flush(self):
    data = self.__wbuf.getvalue()
    encoded = self.sasl.wrap(data)
    self.transport.write(''.join((pack("!i", len(encoded)), encoded)))
    self.transport.flush()
    self.__wbuf = StringIO()

  def read(self, sz):
    ret = self.__rbuf.read(sz)
    if len(ret) != 0:
      return ret

    self._read_frame()
    return self.__rbuf.read(sz)

  def _read_frame(self):
    header = self.transport.readAll(4)
    length, = unpack('!i', header)
    encoded = self.transport.readAll(length)
    self.__rbuf = StringIO(self.sasl.unwrap(encoded))

  def close(self):
    self.sasl.dispose()
    self.transport.close()

  # based on TFramedTransport
  @property
  def cstringio_buf(self):
    return self.__rbuf

  def cstringio_refill(self, prefix, reqlen):
    # self.__rbuf will already be empty here because fastbinary doesn't
    # ask for a refill until the previous buffer is empty.  Therefore,
    # we can start reading new frames immediately.
    while len(prefix) < reqlen:
      self._read_frame()
      prefix += self.__rbuf.getvalue()
    self.__rbuf = StringIO(prefix)
    return self.__rbuf
