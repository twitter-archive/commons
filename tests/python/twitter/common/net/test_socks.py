from twitter.common.net.socks import (
    SocksiPyConnection,
    SocksiPyHandler,
    urllib_opener
)

import mox
import socks


def make_handler(*args, **kw):
  opener = urllib_opener(*args, **kw)
  our_handler = None
  for handler in opener.handlers:
    if isinstance(handler, SocksiPyHandler):
      our_handler = handler
      break
  assert our_handler is not None
  return our_handler


def test_constructor_unwrapping():
  handler = make_handler('foo', 1234, password='bork')
  connection = handler.build_connection('www.google.com', port=80)
  assert connection._proxyargs == (
      socks.PROXY_TYPE_SOCKS5, 'foo', 1234, True, None, 'bork')


def test_mock_connect():
  m = mox.Mox()

  class MockSocket(object):
    def setproxy(self, *args):
      self.args = args
    def settimeout(self, value):
      self.timeout = value
    def connect(self, tup):
      self.host, self.port = tup

  mock_socket = MockSocket()

  m.StubOutWithMock(socks, 'socksocket')
  socks.socksocket().AndReturn(mock_socket)
  m.ReplayAll()

  handler = make_handler('foo', 1234)
  conn = handler.build_connection('www.google.com', timeout=1)
  conn.connect()
  assert mock_socket.args == conn._proxyargs
  assert mock_socket.timeout == 1
  assert mock_socket.host == 'www.google.com'
  assert mock_socket.port == 80

  m.UnsetStubs()
  m.VerifyAll()
