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

import copy
import os
import threading
import types

import bottle

__all__ = (
    'abort',
    'HttpServer',
    'mako_view',
    'redirect',
    'request',
    'response',
    'route',
    'static_file',
    'view',
)


class HttpServer(object):
  """
    Wrapper around bottle to make class-bound servers a little easier
    to write.

    Three illustrative examples:

    Basic encapsulated server:
      from twitter.common.http import HttpServer, route

      class MyServer(HttpServer):
        @route("/hello")
        @route("/hello/:first")
        @route("/hello/:first/:last")
        def hello(self, first='Zaphod', last='Beeblebrox'):
          return 'Hello, %s %s!' % (first, last)

      server = MyServer()
      server.run('localhost', 8888)

    Using a mixin pattern:
      class HelloMixin(object):
        @route("/hello")
        def hello(self):
          return 'Hello!'

      class GoodbyeMixin(object):
        @route("/goodbye")
        def goodbye(self):
          return 'Goodbye!'

      # mixin directly by subclassing
      class MyServerUno(HttpServer, HelloMixin, GoodbyeMixin):
        pass

      # or instead mixin dynamically
      class MyServerDos(HttpServer):
        pass
      server = MyServerDos()
      server.mount_routes(HelloMixin())
      server.mount_routes(GoodbyeMixin())

    Plugin handling:
      Bottle supports plugins.  You can manually specify the plugin for a route with the 'apply'
      keyword argument:

      class DiagnosticsEndpoints(object):
        @route('/vars', apply=[TimerPlugin()])
        def vars(self):
          return self.metrics.sample()

      but if you'd like to have a plugin apply to all methods routed within a particular class,
      you can set the class attribute 'plugins':

      class DiagnosticsEndpoints(object):
        plugins = [TimerPlugin()]
        skip_plugins = []

        @route('/vars')
        def vars(self):
          return self.metrics.sample()

        @route('/ping', apply=[BasicAuth(require_group='mesos')])
        def ping(self):
          return 'pong'

        @route('/health', skip=['TimerPlugin'])
          return 'ok'

      This attribute will be mixed-in after plugins specified on a per route basis.  You may also
      specify 'skip_plugins' at the class-level or 'skip' at the route-level which is a list of
      plugins/plugin names to not apply to the routes.

      This also makes it possible to mix-in authentication classes, e.g.

      class AuthenticateEverything(object):
        plugins = [BasicAuth()]

      class MyApplication(AuthenticateEverything):
        @route('/list/:name')
        def list_by_name(self, name):
          ...
  """

  ROUTES_ATTRIBUTE = '__routes__'
  VIEW_ATTRIBUTE = '__view__'
  ERROR_ATTRIBUTE = '__errors__'

  abort = staticmethod(bottle.abort)
  request = Request = bottle.request
  response = Response = bottle.response
  redirect = staticmethod(bottle.redirect)
  static_file = staticmethod(bottle.static_file)

  @classmethod
  def route(cls, *args, **kwargs):
    """Route a request to a callback.  For the route format, see:
       http://bottlepy.org/docs/dev/tutorial.html#request-routing"""
    # Annotates the callback function with a set of applicable routes rather than registering
    # the route with the global application.  This allows us to mount routes at instantiation
    # time rather than at route declaration time.
    def annotated(function):
      if not hasattr(function, cls.ROUTES_ATTRIBUTE):
        setattr(function, cls.ROUTES_ATTRIBUTE, [])
      getattr(function, cls.ROUTES_ATTRIBUTE).append((args, kwargs))
      return function
    return annotated

  @classmethod
  def view(cls, *args, **kwargs):
    """Postprocess the output of this method with a view.  For more information see:
       http://bottlepy.org/docs/dev/tutorial.html#templates"""
    # Annotates the callback function with a set of applicable views a la HttpServer.route above.
    def annotated(function):
      setattr(function, cls.VIEW_ATTRIBUTE, (args, kwargs))
      return function
    return annotated

  @classmethod
  def error(cls, error_code):
    def annotated(function):
      if not hasattr(function, cls.ERROR_ATTRIBUTE):
        setattr(function, cls.ERROR_ATTRIBUTE, [])
      getattr(function, cls.ERROR_ATTRIBUTE).append(error_code)
      return function
    return annotated

  @classmethod
  def mako_view(cls, *args, **kwargs):
    """Helper function for annotating mako-specific views."""
    kwargs.update(template_adapter=bottle.MakoTemplate)
    return cls.view(*args, **kwargs)

  @classmethod
  def set_content_type(cls, header_value):
    cls.response.content_type = header_value

  def __init__(self):
    self._app = bottle.Bottle()
    self._hostname = None
    self._port = None
    self._mounts = set()
    self.mount_routes(self)

  # Delegate to the underlying Bottle application
  def __getattr__(self, attr):
    return getattr(self._app, attr)

  @classmethod
  def source_name(cls, class_or_instance):
    return getattr(class_or_instance, '__name__', class_or_instance.__class__.__name__)

  def _bind_method(self, class_instance, method_name):
    """
      Delegate class_instance.method_name to self.method_name
    """
    if not hasattr(class_instance, method_name):
      raise ValueError('No method %s.%s exists for bind_method!' % (
        self.source_name(class_instance), method_name))
    if isinstance(getattr(class_instance, method_name), types.MethodType):
      method_self = getattr(class_instance, method_name).im_self
      if method_self is None:
        # I attempted to allow for an unbound class pattern but failed.  The Python interpreter
        # allows for types.MethodType(cls.f, cls(), cls) to bind properly, but (cls.f, self, cls)
        # cannot unless self is in the cls MRO chain which is not guaranteed if cls just derives
        # from a vanilla object.
        raise TypeError('Cannot mount methods from an unbound class.')
      self._mounts.add(method_self)
      setattr(self, method_name, getattr(class_instance, method_name))

  @classmethod
  def _apply_plugins(cls, class_instance, kw):
    plugins = kw.get('apply', [])
    skiplist = kw.get('skip', [])
    class_plugins = getattr(class_instance, 'plugins', [])
    class_skiplist = getattr(class_instance, 'skiplist', [])
    kw.update(apply=plugins + class_plugins, skip=skiplist + class_skiplist)
    return kw

  def mount_routes(self, class_instance):
    """
      Mount the routes from another class instance.

      The routes must be added to the class via the HttpServer.route annotation and not directly
      from the bottle.route decorator.
    """
    for callback_name in dir(class_instance):
      callback = getattr(class_instance, callback_name)
      if hasattr(callback, self.ROUTES_ATTRIBUTE) or hasattr(callback, self.ERROR_ATTRIBUTE):
        # Bind the un-annotated callback to this class
        self._bind_method(class_instance, callback_name)
        # Apply view annotations
        if hasattr(callback, self.VIEW_ATTRIBUTE):
          args, kw = getattr(callback, self.VIEW_ATTRIBUTE)
          callback = bottle.view(*args, **kw)(callback)
          setattr(self, callback_name, callback)
        # Apply route annotations
        for args, kw in getattr(callback, self.ROUTES_ATTRIBUTE, ()):
          kw = self._apply_plugins(class_instance, copy.deepcopy(kw))
          kw.update(callback=callback)
          self._app.route(*args, **kw)
        for error_code in getattr(callback, self.ERROR_ATTRIBUTE, ()):
          self._app.error(error_code)(callback)

  @property
  def app(self):
    """
      Return the bottle app object associated with this HttpServer instance.
    """
    return self._app

  @property
  def hostname(self):
    return self._hostname

  @property
  def port(self):
    return self._port

  def run(self, hostname, port, server='wsgiref'):
    """
      Start a webserver on hostname & port.
    """
    self._hostname = hostname
    self._port = port
    self._app.run(host=hostname, port=port, server=server)

  def __str__(self):
    return 'HttpServer(%s, mixins: %s)' % (
        '%s:%s' (self.hostname, self.port) if self.hostname else 'unbound',
        ', '.join(self.source_name(instance) for instance in self._mounts))


abort = HttpServer.abort
mako_view = HttpServer.mako_view
redirect = HttpServer.redirect
request = HttpServer.request
response = HttpServer.response
route = HttpServer.route
static_file = HttpServer.static_file
view = HttpServer.view
