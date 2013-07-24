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

import functools
import wsgiref.util

from twitter.common.http import HttpServer

import pytest


skipifpy3k = pytest.mark.skipif('sys.version_info >= (3,0)')


def make_request(path):
  test_req = {'REQUEST_METHOD': 'GET', 'PATH_INFO': path}
  wsgiref.util.setup_testing_defaults(test_req)
  return test_req


def response_asserter(intended, status, headers):
  assert int(status.split()[0]) == intended


# TODO(wickman) Fix bind method delegation in py3x.  It's currently brittle
# and the new module might actually allow for binding in this fashion now.
@skipifpy3k
def test_basic_server_method_binding():
  class MyServer(HttpServer):
    def __init__(self):
      HttpServer.__init__(self)
    @HttpServer.route("/hello")
    @HttpServer.route("/hello/:first")
    @HttpServer.route("/hello/:first/:last")
    def hello(self, first = 'Zaphod', last = 'Beeblebrox'):
      return 'Hello, %s %s!' % (first, last)

  server = MyServer()
  assert server.app.handle('/hello') == 'Hello, Zaphod Beeblebrox!'
  assert server.app.handle('/hello/Brian') == 'Hello, Brian Beeblebrox!'
  assert server.app.handle('/hello/Brian/Horfgorf') == 'Hello, Brian Horfgorf!'



@skipifpy3k
def test_basic_server_error_binding():
  BREAKAGE = '*****breakage*****'
  class MyServer(object):
    @HttpServer.route('/broken')
    def broken_handler(self):
      raise Exception('unhandled exception!')

    @HttpServer.error(404)
    @HttpServer.error(500)
    def error_handler(self, error):
      return BREAKAGE

  server = HttpServer()
  mserver = MyServer()
  server.mount_routes(mserver)

  # Test 404 error handling.
  resp = server.app(make_request('/nonexistent_page'), functools.partial(response_asserter, 404))
  assert resp[0] == BREAKAGE

  # Test 500 error handling.
  resp = server.app(make_request('/broken'), functools.partial(response_asserter, 500))
  assert resp[0] == BREAKAGE


@skipifpy3k
def test_bind_method():
  class BaseServer(HttpServer):
    NAME = "heavens to murgatroyd!"
    def __init__(self):
      self._name = BaseServer.NAME
      HttpServer.__init__(self)

  class BaseServerNotSubclass(object):
    def method_one(self):
      return 'method_one'

  class BaseServerIsSubclass(BaseServer):
    def method_two(self):
      return 'method_two'

  bs = BaseServer()

  # make sure we properly raise un nonexistent methods
  with pytest.raises(ValueError):
    bs._bind_method(BaseServerIsSubclass, 'undefined_method_name')

  # properly raise on classes w/ divergent parents
  with pytest.raises(TypeError):
    bs._bind_method(BaseServerNotSubclass, 'method_one')

  # should be able to bind method to base class self
  bs._bind_method(BaseServerNotSubclass(), 'method_one')
  bs._bind_method(BaseServerIsSubclass(), 'method_two')
  assert bs.method_one() == 'method_one'
  assert bs.method_two() == 'method_two'
