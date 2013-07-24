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

import threading

from twitter.common import app, options
from twitter.common.exceptions import ExceptionalThread
from twitter.common.http.diagnostics import DiagnosticsEndpoints
from twitter.common.http.server import HttpServer


class LifecycleEndpoints(object):
  @HttpServer.route('/quitquitquit', method='POST')
  def quitquitquit(self):
    app.quitquitquit()

  @HttpServer.route('/abortabortabort', method='POST')
  def abortabortabort(self):
    app.abortabortabort()


class RootServer(HttpServer, app.Module):
  """
    A root singleton server for all your http endpoints to bind to.
  """

  OPTIONS = {
    'enable':
      options.Option('--enable_http',
          default=False,
          action='store_true',
          dest='twitter_common_http_root_server_enabled',
          help='Enable root http server for various subsystems, e.g. metrics exporting.'),

    'disable_lifecycle':
      options.Option('--http_disable_lifecycle',
          default=False,
          action='store_true',
          dest='twitter_common_http_root_server_disable_lifecycle',
          help='Disable the lifecycle commands, i.e. /quitquitquit and /abortabortabort.'),

    'port':
      options.Option('--http_port',
          default=8888,
          type='int',
          metavar='PORT',
          dest='twitter_common_http_root_server_port',
          help='The port the root http server will be listening on.'),

    'host':
      options.Option('--http_host',
          default='localhost',
          type='string',
          metavar='HOSTNAME',
          dest='twitter_common_http_root_server_host',
          help='The host the root http server will be listening on.'),

    'framework':
      options.Option('--http_framework',
          default='wsgiref',
          type='string',
          metavar='FRAMEWORK',
          dest='twitter_common_http_root_server_framework',
          help='The framework that will be running the integrated http server.')
  }

  def __init__(self):
    self._thread = None
    HttpServer.__init__(self)
    app.Module.__init__(self, __name__, description="Http subsystem.")

  def setup_function(self):
    assert self._thread is None, "Attempting to call start() after server has been started!"
    options = app.get_options()
    parent = self

    self.mount_routes(DiagnosticsEndpoints())
    if not options.twitter_common_http_root_server_disable_lifecycle:
      self.mount_routes(LifecycleEndpoints())

    class RootServerThread(ExceptionalThread):
      def __init__(self):
        super(RootServerThread, self).__init__()
        self.daemon = True

      def run(self):
        rs = parent
        rs.run(options.twitter_common_http_root_server_host,
               options.twitter_common_http_root_server_port,
               server=options.twitter_common_http_root_server_framework)

    if options.twitter_common_http_root_server_enabled:
      self._thread = RootServerThread()
      self._thread.start()
