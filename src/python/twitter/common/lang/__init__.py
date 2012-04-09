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

__author__ = 'Brian Wickman'

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
    from io import BytesIO

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

try:
  from functools import total_ordering
except ImportError:
  # Taken from Library/Frameworks/Python.framework/Versions/2.7/lib/python2.7/functools.py
  def total_ordering(cls):
      """Class decorator that fills in missing ordering methods"""
      convert = {
          '__lt__': [('__gt__', lambda self, other: not (self < other or self == other)),
                     ('__le__', lambda self, other: self < other or self == other),
                     ('__ge__', lambda self, other: not self < other)],
          '__le__': [('__ge__', lambda self, other: not self <= other or self == other),
                     ('__lt__', lambda self, other: self <= other and not self == other),
                     ('__gt__', lambda self, other: not self <= other)],
          '__gt__': [('__lt__', lambda self, other: not (self > other or self == other)),
                     ('__ge__', lambda self, other: self > other or self == other),
                     ('__le__', lambda self, other: not self > other)],
          '__ge__': [('__le__', lambda self, other: (not self >= other) or self == other),
                     ('__gt__', lambda self, other: self >= other and not self == other),
                     ('__lt__', lambda self, other: not self >= other)]
      }
      roots = set(dir(cls)) & set(convert)
      if not roots:
          raise ValueError('must define at least one ordering operation: < > <= >=')
      root = max(roots)       # prefer __lt__ to __le__ to __gt__ to __ge__
      for opname, opfunc in convert[root]:
          if opname not in roots:
              opfunc.__name__ = opname
              opfunc.__doc__ = getattr(int, opname).__doc__
              setattr(cls, opname, opfunc)
      return cls


class Compatibility(object):
  """2.x + 3.x compatibility"""
  PY2 = sys_version_info[0] == 2
  PY3 = sys_version_info[0] == 3
  StringIO = StringIO
  BytesIO = BytesIO if PY3 else StringIO

  integer = (Integral,)
  real = (Real,)
  numeric = integer + real
  string = (str,) if PY3 else (str, unicode)
  bytes = (bytes,)

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
