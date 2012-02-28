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

from sys import version_info as sys_version_info
from numbers import Integral, Real

try:
  # CPython 2.x
  from cStringIO import StringIO
except ImportError:
  try:
    # Python 2.x
    from StringIO import StringIO
  except:
    # Python 3.x
    from io import StringIO

class SingletonMetaclass(type):
  """
    Singleton metaclass.
  """
  def __init__(cls, name, bases, attrs):
    super(SingletonMetaclass, cls).__init__(name, bases, attrs)
    cls.instance = None

  def __call__(cls, *args, **kw):
    if cls.instance is None:
      cls.instance = super(SingletonMetaclass, cls).__call__(*args, **kw)
    return cls.instance

Singleton = SingletonMetaclass('Singleton', (object,), {})

class Compatibility(object):
  """2.x + 3.x compatibility"""
  PY2 = sys_version_info[0] == 2
  PY3 = sys_version_info[0] == 3
  StringIO = StringIO

  integer = (Integral,)
  real = (Real,)
  numeric = integer + real
  string = (str,) if PY3 else (str, unicode)

  if PY2:
    @staticmethod
    def to_bytes(st):
      return str(st)
  else:
    @staticmethod
    def to_bytes(st):
      return bytes(st, encoding='utf8')

  if PY3:
    @staticmethod
    def exec_function(ast, globals_map):
      locals_map = globals_map
      exec(ast, globals_map, locals_map)
      return locals_map
  else:
    eval(compile(
"""
@staticmethod
def exec_function(ast, globals_map):
  locals_map = globals_map
  exec ast in globals_map, locals_map
  return locals_map
""", "<exec_function>", "exec"))

__all__ = [
  'Singleton',
  'Compatibility',
]
