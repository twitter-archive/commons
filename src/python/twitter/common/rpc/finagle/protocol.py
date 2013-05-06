import functools
import threading
from thrift.Thrift import (
  TMessageType,
  TApplicationException)
from thrift.protocol import TBinaryProtocol
from gen.twitter.finagle.thrift.ttypes import (
  ConnectionOptions,
  ClientId,
  UpgradeReply,
  RequestHeader,
  ResponseHeader)

from twitter.common.rpc.finagle.trace import Trace

def upgrade_protocol_to_finagle(protocol):
  UPGRADE_METHOD = "__can__finagle__trace__v3__"

  def send(protocol):
    protocol.writeMessageBegin(UPGRADE_METHOD, TMessageType.CALL, 0)
    connection_options = ConnectionOptions()
    connection_options.write(protocol)
    protocol.writeMessageEnd()
    protocol.trans.flush()

  def recv(protocol):
    (fname, mtype, rseqid) = protocol.readMessageBegin()
    if fname != UPGRADE_METHOD:
      raise Exception('Unexpected error upgrading Finagle transport!')
    if mtype == TMessageType.EXCEPTION:
      exc = TApplicationException()
      exc.read(protocol)
      protocol.readMessageEnd()
      raise exc
    reply = UpgradeReply()
    reply.read(protocol)
    protocol.readMessageEnd()
    return reply

  send(protocol)
  return recv(protocol)

class TFinagleProtocol(TBinaryProtocol.TBinaryProtocolAccelerated):
  def __init__(self, *args, **kw):
    self._locals = threading.local()
    self._finagle_upgraded = False
    self._client_id = kw.pop('client_id', None)
    self._client_id = ClientId(name=self._client_id) if self._client_id else None
    TBinaryProtocol.TBinaryProtocolAccelerated.__init__(self, *args, **kw)
    try:
      upgrade_protocol_to_finagle(self)
      self._finagle_upgraded = True
    except TApplicationException:
      pass

  def to_request_header(self, trace_id):
    return RequestHeader(trace_id=trace_id.trace_id.value,
                         parent_span_id=trace_id.parent_id.value,
                         span_id=trace_id.span_id.value,
                         sampled=trace_id.sampled,
                         client_id=self._client_id)

  def writeMessageBegin(self, *args, **kwargs):
    if self._finagle_upgraded:
      if not hasattr(self._locals, 'trace'):
        self._locals.trace = Trace()
      trace_id = self._locals.trace.get()
      self.to_request_header(trace_id).write(self)
      with self._locals.trace.push(trace_id):
        return TBinaryProtocol.TBinaryProtocolAccelerated.writeMessageBegin(self, *args, **kwargs)
    else:
      return TBinaryProtocol.TBinaryProtocolAccelerated.writeMessageBegin(self, *args, **kwargs)

  def readMessageBegin(self, *args, **kwargs):
    if self._finagle_upgraded:
      header = ResponseHeader()
      header.read(self)
      self._locals.last_response = header
    return TBinaryProtocol.TBinaryProtocolAccelerated.readMessageBegin(self, *args, **kwargs)


def TFinagleProtocolWithClientId(client_id):
  return functools.partial(TFinagleProtocol, client_id=client_id)
