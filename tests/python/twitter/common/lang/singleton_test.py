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

from twitter.common.lang import Singleton, SingletonMetaclass

def test_basic_singleton():
  class Hello(Singleton):
    def __init__(self):
      pass
  h1 = Hello()
  h2 = Hello()
  assert id(h1) == id(h2), 'singleton mixin should memoize objects'

def test_basic_singleton_metaclass():
  class Hello(object):
    __metaclass__ = SingletonMetaclass
    def __init__(self):
      pass
  h1 = Hello()
  h2 = Hello()
  assert id(h1) == id(h2), 'singleton metaclass should memoize objects'

def test_singleton_names():
  class Hello(object):
    __metaclass__ = SingletonMetaclass
    def __init__(self, sig):
      self._sig = sig
  H1 = Hello

  class Hello(object):
    __metaclass__ = SingletonMetaclass
    def __init__(self, sig):
      self._sig = sig
  H2 = Hello

  h1 = H1('a')
  h2 = H2('b')
  assert id(h1) != id(h2), 'class names should not matter with singleton decorator'
  assert h1._sig != h2._sig

def test_cannot_supercede_constructors():
  class CountingSingleton(object):
    __metaclass__ = SingletonMetaclass
    VALUE=0
    @staticmethod
    def increment():
      CountingSingleton.VALUE += 1
    def __init__(self, value):
      self._value = value
      CountingSingleton.increment()
    def value(self):
      return self._value

  s1 = CountingSingleton('a')
  assert CountingSingleton.VALUE == 1, 'singleton constructor should be called on first invocation'

  s2 = CountingSingleton('b')
  assert s1.value() == s2.value()
  assert s2.value() == 'a'
  assert CountingSingleton.VALUE == 1, 'the constructor of a singleton should never be called twice'

def assert_factories_consistent(john, brian):
  j1 = john()
  j2 = john()
  b1 = brian()
  b2 = brian()
  assert id(j1) == id(j2)
  assert id(b1) == id(b2)
  assert id(j1) != id(b1)

def test_singleton_mixin_inheritance():
  class Named(object):
    def __init__(self, name):
      self._name = name
  class John(Named, Singleton):
    def __init__(self):
      Named.__init__(self, 'John')
  class Brian(Named, Singleton):
    def __init__(self):
      Named.__init__(self, 'Brian')
  assert_factories_consistent(John, Brian)
  # make sure ordering makes no difference
  class John(Singleton, Named):
    def __init__(self):
      Named.__init__(self, 'John')
  class Brian(Singleton, Named):
    def __init__(self):
      Named.__init__(self, 'Brian')
  assert_factories_consistent(John, Brian)


def test_singleton_metaclass_inheritance():
  class Named(object):
    __metaclass__ = SingletonMetaclass
    def __init__(self, name):
      self._name = name
  class John(Named):
    def __init__(self):
      Named.__init__(self, 'John')
  class Brian(Named):
    def __init__(self):
      Named.__init__(self, 'Brian')
  assert_factories_consistent(John, Brian)
