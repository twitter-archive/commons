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

"""
  Basic Twitter Python application framework.

  Example usage:
    from twitter.common import app, log
    app.add_option('--my_option', dest='my_option', help="my commandline argument!")

    def main(args):
      log.info('my_option was set to %s' % app.get_options().my_option)
      log.info('my argv is: %s' % args)

    app.main()

  app.main() replaces the "if __name__ == '__main__': main()" idiom and runs any
  initialization code by app-compatible libraries as many in twitter.common are.
"""

import sys
import types

from twitter.common.lang import Compatibility

from .application import Application
from .module import AppModule as Module


# Initialize the global application
reset = Application.reset
reset()


ApplicationError = Application.Error


def _make_proxy_function(method_name):
  unbound_method = Application.__dict__[method_name]
  def proxy_function(*args, **kwargs):
    if Compatibility.PY2:
      bound_method = types.MethodType(unbound_method,
                                      Application.active(),
                                      Application)
    else:
      bound_method = types.MethodType(unbound_method,
                                      Application.active())
    return bound_method(*args, **kwargs)
  proxy_function.__doc__ = getattr(Application, attribute).__doc__
  proxy_function.__name__ = attribute
  return proxy_function


__all__ = [
  'reset',
  'ApplicationError',
  'Module',
]


# TODO(wickman) This is a ghastly pattern that is not even guaranteed to
# work with all interpreters and should be reworked.
#
# create a proxy function for every public method in Application and delegate that
# to the module namespace, using the active _APP object (which can be reset by
# reset() for testing.)
for attribute in Application.__dict__:
  if attribute.startswith('_'): continue
  if type(Application.__dict__[attribute]) == types.FunctionType:
    locals()[attribute] = _make_proxy_function(attribute)
    __all__.append(attribute)
