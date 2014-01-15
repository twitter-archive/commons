"""
  A SOCKS HTTPConnection handler.  Adapted from https://gist.github.com/e000/869791
"""

from __future__ import absolute_import

import socket

import socks

try:
  from http.client import HTTPConnection
  import urllib.request as urllib_request
except ImportError:
  from httplib import HTTPConnection
  import urllib2 as urllib_request

__all__ = ('opener',)


class SocksiPyConnection(HTTPConnection):
  def __init__(self, proxytype,
                     proxyaddr,
                     proxyport=None,
                     rdns=True,
                     username=None,
                     password=None,
                     *args,
                     **kwargs):
    self._proxyargs = (proxytype, proxyaddr, proxyport, rdns, username, password)
    HTTPConnection.__init__(self, *args, **kwargs)

  def connect(self):
    self.sock = socks.socksocket()
    self.sock.setproxy(*self._proxyargs)

    # Most Python variants use socket._GLOBAL_DEFAULT_TIMEOUT as the socket timeout.
    # Unfortunately this is an object() sentinel, and sock.settimeout requires a float.
    # What were they thinking?
    if not hasattr(socket, '_GLOBAL_DEFAULT_TIMEOUT') or (
        self.timeout != socket._GLOBAL_DEFAULT_TIMEOUT):
      self.sock.settimeout(self.timeout)

    # SocksiPy has this gem:
    #   if type(self.host) != type('')
    # which breaks should it get a host in unicode form in 2.x.  sigh.
    self.sock.connect((str(self.host), self.port))


class SocksiPyHandler(urllib_request.HTTPHandler):
  def __init__(self, *args, **kwargs):
   self._args = args
   self._kw = kwargs
   urllib_request.HTTPHandler.__init__(self)

  def build_connection(self, host, port=None, strict=None, timeout=None):
    return SocksiPyConnection(*self._args, host=host, port=port, strict=strict,
        timeout=timeout, **self._kw)

  def http_open(self, req):
    return self.do_open(self.build_connection, req)


def urllib_opener(proxy_host, proxy_port, proxy_type=socks.PROXY_TYPE_SOCKS5, **kw):
  """
    Construct a proxied urllib opener via the SOCKS proxy at proxy_host:proxy_port.

    proxy_type may be socks.PROXY_TYPE_SOCKS4 or socks.PROXY_TYPE_SOCKS5, by
    default the latter.  the remaining keyword arguments will be passed to SocksiPyHandler,
    e.g. rdns, username, password.
  """
  return urllib_request.build_opener(SocksiPyHandler(proxy_type, proxy_host, proxy_port, **kw))
