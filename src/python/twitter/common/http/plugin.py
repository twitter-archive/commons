from __future__ import print_function

from abc import abstractmethod
from functools import wraps
import pprint
import time

from twitter.common.lang import Interface

from .server import request


class Plugin(Interface):
  @property
  def name(self):
    """The name of the plugin."""
    return self.__class__.__name__

  @property
  def api(self):
    # This Plugin is the duck-typed Bottle Plugin interface v2.
    return 2

  def setup(self, app):
    pass

  @abstractmethod
  def apply(self, callback, route):
    """Given the Bottle callback and Route object, return a (possibly)
       decorated version of the original callback function, e.g. a
       version that profiles the endpoint.

       For more information see:
         http://bottlepy.org/docs/stable/plugindev.html
    """

  def close(self):
    pass


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
