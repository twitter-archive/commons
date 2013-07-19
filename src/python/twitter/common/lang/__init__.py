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
from .lockable import Lockable


# StringIO / BytesIO
# TODO(wickman)  Since the io package is available in 2.6.x, use that instead of
# cStringIO/StringIO
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


# Singletons
class SingletonMetaclass(type):
  """
    Singleton metaclass.
  """
  def __call__(cls, *args, **kw):
    if not hasattr(cls, 'instance'):
      cls.instance = super(SingletonMetaclass, cls).__call__(*args, **kw)
    return cls.instance

Singleton = SingletonMetaclass('Singleton', (object,), {})


# total_ordering
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


# Abstract base classes w/o __metaclass__ or meta =
from abc import ABCMeta
AbstractClass = ABCMeta('AbstractClass', (object,), {})


# Coroutine initialization
def coroutine(func):
  def start(*args, **kwargs):
    cr = func(*args, **kwargs)
    cr.next()
    return cr
  return start


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
  'AbstractClass',
  'Compatibility',
  'Lockable',
  'Singleton',
]


class InheritDocstringsMetaclass(type):
  """
  For each method in a (sub)class without a defined docstring, inherit the docstring for the method
  from a parent class, if it exists. Useful mostly for abstract class/interface definitions.

    Example usage:
       >>> class Foo(object):
       ...   def my_method(self):
       ...     '''This method has a nice docstring!'''
       ...     print "In Foo"
       ...
       >>> class Bar(Foo):
       ...   __metaclass__ = InheritDocstringsMetaclass
       ...   def my_method(self):
       ...     print "In Bar"
       ...
       >>> Bar().my_method.__doc__
       'This method has a nice docstring!'
       >>> Bar().my_method()
       In Bar

  """
  def __new__(self, class_name, bases, namespace):
    for key, value in namespace.items():
      if callable(value) and not value.__doc__:
        for parent in bases:
          if hasattr(parent, key) and getattr(parent, key).__doc__:
            value.__doc__ = getattr(parent, key).__doc__
            break
    return type.__new__(self, class_name, bases, namespace)


class InterfaceMetaclass(ABCMeta, InheritDocstringsMetaclass): pass
Interface = InterfaceMetaclass('Interface', (object, ), {})
