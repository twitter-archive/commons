from __future__ import print_function
from functools import wraps
import pprint

from twitter.common.http.plugin import Plugin

from ..server import request


class EchoHeaders(Plugin):
  """An example Plugin that prints to stdout request information as it comes in."""

  def apply(self, callback, route):
    @wraps(callback)
    def wrapped_callback(*args, **kw):
      print('path: %s' % request.path)
      print('method: %s' % request.method)
      print('cookies: ', end='')
      pprint.pprint(dict(request.cookies.items()))
      print('query: ', end='')
      pprint.pprint(dict(request.query.items()))
      print('params: ', end='')
      pprint.pprint(dict(request.params.items()))
      print('headers: ', end='')
      pprint.pprint(dict(request.headers.items()))
      return callback(*args, **kw)
    return wrapped_callback
