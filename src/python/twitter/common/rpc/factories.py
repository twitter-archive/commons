import functools
from thrift.transport import TSocket, TTransport
from thrift.protocol import TBinaryProtocol
from twitter.common.rpc.address import Address


class ConnectionClosable(object):
  """Mixin class for thrift connection closability."""

  def close(self):
    if hasattr(self, '_connection') and self._connection:
      self._connection.close()
    return

  def __enter__(self):
    return self

  def __exit__(self, *args, **kwargs):
    self.close()
    return False


class ConnectionFactory(object):
  def __init__(self, connection_klazz):
    self._connection_klazz = connection_klazz

  def __call__(self, *args, **kw):
    address = Address.parse(*args, **kw)
    return self._connection_klazz(host=address.host, port=address.port)


class TransportFactory(object):
  def __init__(self, transport_klazz):
    self._transport_klazz = transport_klazz

  def __call__(self, connection):
    return self._transport_klazz(connection)


class ProtocolFactory(object):
  def __init__(self, protocol_klazz):
    self._protocol_klazz = protocol_klazz

  def __call__(self, transport):
    return self._protocol_klazz(transport)


class ClientFactory(object):
  def __init__(self, client_iface, connection):
    self._client_iface = client_iface
    self._connection = connection

  def _mixin(self, klazz, mixin):
    if mixin not in klazz.__bases__:
      klazz.__bases__ += (mixin,)
    return klazz

  def __call__(self, protocol):
    # Mix ConnectionClosable class into the client class.
    client_class = getattr(self._client_iface, 'Client')
    client_class = self._mixin(client_class, ConnectionClosable)

    # Instantiate and set _connection object on the instance.
    client = client_class(protocol)
    client._connection = self._connection

    return client


def make_client(client_iface, *args, **kw):
  """
    Basic usage:

    >>> make_client(UserService, 'localhost', 9999)

    or make an SSL socket server (see also make_server)

    >>> make_client(UserService, 'smf1-amk-25-sr1.prod.twitter.com', 9999,
    ...             connection=TSocket.TSSLServerSocket,
    ...             ca_certs=...)

    Make a finagle client:

    >>> make_client(UserService, 'localhost', 9999,
    ...             protocol=TFinagleProtocol)

    And one with a client_id

    >>> make_client(UserService, 'localhost', 9999,
    ...             protocol=functools.partial(TFinagleProtocol, client_id="test_client"))

    this is equivalent to

    >>> make_client(UserService, 'localhost', 9999,
    ...             protocol=TFinagleProtocolWithClientId("test_client")))

    Bind to a unix_socket instead of host/port pair (unix_socket is kwarg to
    TSocket.TSocket)

    >>> make_client(UserService, unix_socket=...opened-fifo...)

    N.B. This can also be used as a contextmanager or with contextlib.closing to
         automatically handle closing of the thrift connection (or manually via close()).

    >>> with make_client(...) as c:
    ...   c.somefunc()

  """
  protocol_class = kw.pop('protocol', TBinaryProtocol.TBinaryProtocolAccelerated)
  transport_class = kw.pop('transport', TTransport.TFramedTransport)
  connection_class = kw.pop('connection', TSocket.TSocket)

  connection = ConnectionFactory(connection_class)(*args, **kw)
  connection.open()

  return ClientFactory(client_iface, connection)(
           ProtocolFactory(protocol_class)(
             TransportFactory(transport_class)(connection)))


def make_client_factory(client_iface):
  return functools.partial(make_client, client_iface)
