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

import os
import copy
import types
import threading

import bottle

class HttpServer(object):
  """
    Wrapper around bottle to make class-bound servers a little easier
    to write.

    Three illustrative examples:

    Basic encapsulated server:
      class MyServer(HttpServer):
        def __init__(self):
          HttpServer.__init__(self)

        @HttpServer.route("/hello")
        @HttpServer.route("/hello/:first")
        @HttpServer.route("/hello/:first/:last")
        def hello(self, first = 'Zaphod', last = 'Beeblebrox'):
          return 'Hello, %s %s!' % (first, last)

      server = MyServer()
      server.run('localhost', 8888)

    Using a mixin pattern:
      class HelloMixin(object):
        @HttpServer.route("/hello")
        def hello(self):
          return 'Hello!'

      class GoodbyeMixin(object):
        @HttpServer.route("/goodbye")
        def goodbye(self):
          return 'Goodbye!'

      # mixin directly by subclassing
      class MyServerUno(HttpServer, HelloMixin, GoodbyeMixin):
        def __init__(self):
          HttpServer.__init__(self)

      # or instead mixin dynamically (though the mixins encapsulate their
      #  own state and do not have access to the global application state.)
      class MyServerDos(HttpServer):
        def __init__(self):
          HttpServer.__init__(self)
      server = MyServer()
      server.mount_routes(HelloMixin())
      server.mount_routes(GoodbyeMixin())
  """

  ROUTES_ATTRIBUTE = '__routes__'
  VIEW_ATTRIBUTE = '__view__'

  Request = bottle.request
  Response = bottle.HTTPResponse
  redirect = staticmethod(bottle.redirect)

  @staticmethod
  def route(*args, **kwargs):
    def annotated(function):
      if not hasattr(function, HttpServer.ROUTES_ATTRIBUTE):
        setattr(function, HttpServer.ROUTES_ATTRIBUTE, [])
      getattr(function, HttpServer.ROUTES_ATTRIBUTE).append((args, kwargs))
      return function
    return annotated

  @staticmethod
  def view(*args, **kwargs):
    def annotated(function):
      setattr(function, HttpServer.VIEW_ATTRIBUTE, (args, kwargs))
      return function
    return annotated

  @staticmethod
  def mako_view(*args, **kwargs):
    kwargs.update(template_adapter = bottle.MakoTemplate)
    return HttpServer.view(*args, **kwargs)

  @staticmethod
  def abort(*args, **kwargs):
    return bottle.abort(*args, **kwargs)

  @staticmethod
  def set_content_type(header_value):
    bottle.response.content_type = header_value

  def __init__(self):
    self._app = bottle.Bottle()
    self._request = bottle.request   # it's sort of upsetting that these are globals
    self._response = bottle.response # in bottle, but, c'est la vie.
    self._hostname = None
    self._port = None
    self._mounts = set()
    self.mount_routes(self)

  def _bind_method(self, cls, method_name):
    """
      Delegate cls.method_name to self.
    """
    if not hasattr(cls, method_name):
      raise ValueError('No method %s.%s exists for bind_method!' % (
        cls.__name__ if hasattr(cls, '__name__') else cls.__class__.__name__,
        method_name))
    if isinstance(getattr(cls, method_name), types.MethodType):
      method_self = getattr(cls, method_name).im_self
      if method_self is not None:  # pre-bound method, save
        self._mounts.add(method_self)
        setattr(self, method_name, getattr(cls, method_name))
      else:
        # I tried and failed...the Python MRO hates me, specifically:
        #   types.MethodType(klz.f, klz(), klz) binds, but (klz.f, self, klz) does not unless
        #   self is in the klz MRO chain, which invalidates the whole point of the class-less
        #   mixin pattern.
        raise TypeError('Cannot mount methods from an unbound class.')

  def mount_routes(self, cls):
    """
      Mount the routes from another class.

      The routes must be added to the class via the HttpServer.route annotation.
    """
    for attr in dir(cls):
      class_attr = getattr(cls, attr)
      if hasattr(class_attr, HttpServer.ROUTES_ATTRIBUTE):
        self._bind_method(cls, attr)
        if hasattr(class_attr, HttpServer.VIEW_ATTRIBUTE):
          args, kw = getattr(class_attr, HttpServer.VIEW_ATTRIBUTE)
          setattr(self, attr, bottle.view(*args, **kw)(getattr(self, attr)))
        for args, kwargs in getattr(class_attr, HttpServer.ROUTES_ATTRIBUTE):
          kwargs = copy.deepcopy(kwargs)
          kwargs.update({'callback': getattr(self, attr)})
          self._app.route(*args, **kwargs)

  def app(self):
    """
      Return the bottle app object associated with this HttpServer instance.
    """
    return self._app

  def hostname(self):
    return self._hostname

  def port(self):
    return self._port

  def run(self, hostname, port, server='wsgiref'):
    """
      Start a webserver on hostname & port.
    """
    self._hostname = hostname
    self._port = port
    bottle.run(self._app, host=hostname, port=port, server=server)
