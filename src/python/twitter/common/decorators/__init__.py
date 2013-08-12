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

from __future__ import print_function

__author__ = 'Brian Wickman'

import sys
import types

try:
  from twitter.common import log
  _log_function = log.warning
except ImportError:
  def _log_function(msg):
    print(msg, file=sys.stderr)

from .lru_cache import lru_cache
from .threads import identify_thread

__all__ = (
  'deprecated',
  'deprecated_with_warning',
  'identify_thread',
  'lru_cache'
)

def _deprecated_wrap_fn(fn, message=None):
  if not isinstance(fn, types.FunctionType):
    raise ValueError("@deprecated annotation requires a function!")
  def _function(*args, **kwargs):
    _log_function("DEPRECATION WARNING: %s:%s is deprecated!  %s" % (
      _function.__module__,
      _function.__name__,
      message if message is not None else ""))
    return fn(*args, **kwargs)
  _function.__doc__ = fn.__doc__
  _function.__name__ = fn.__name__
  return _function

def deprecated(function):
  """@deprecated annotation.  logs a warning to the twitter common logging framework
     should calls be made to the decorated function.

     e.g.
     @deprecated
     def qsort(lst):
       ...
  """
  return _deprecated_wrap_fn(function)

class deprecated_with_warning(object):
  """@deprecated_with_warning annotation.  logs a warning to the twitter
     common logging framework should calls be made to the decorated
     function, along with an additional supplied message.

     e.g.
     @deprecated_with_warning("Use sort() call instead!")
     def qsort(lst):
       ...
  """
  def __init__(self, message):
    self._msg = message

  def __call__(self, function):
    return _deprecated_wrap_fn(function, self._msg)

