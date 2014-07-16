# ==================================================================================================
# Copyright 2014 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================


import pytest

from twitter.common.collections import OrderedDict


def test_ordered_dict_simple():
  d1 = OrderedDict()
  d1['first'] = 1
  d1['second'] = 2
  d1['third'] = 3
  d1['fourth'] = 4
  d1['fifth'] = 5
  assert d1.keys() == ['first', 'second', 'third', 'fourth', 'fifth']
  assert d1.values() == [1, 2, 3, 4, 5]

  d2 = OrderedDict()
  d2['r'] = 18
  d2['a'] = 32
  d2['n'] = 344
  d2['d'] = 34
  d2['o'] = 45
  d2['m'] = 5
  assert d2.keys() == ['r', 'a', 'n', 'd', 'o', 'm']
  assert d2.values() == [18, 32, 344, 34, 45, 5]


def test_pop():
  d = OrderedDict()
  d['key'] = 'foo'
  assert 'foo' == d.pop('key')
  assert not 'key' in d
  with pytest.raises(KeyError):
    assert d['key'] == None
  assert d.pop('key', 'bar') == 'bar'


def test_setdefault():
  d = OrderedDict()
  d['key'] = 'foo'
  assert 'foo' == d.setdefault('key', 'bar')
  assert d['key'] == 'foo'
  assert 'bar' == d.setdefault('key2', 'bar')
  assert d['key2'] == 'bar'


def test_clear():
  d = OrderedDict()
  d['a'] = 1
  d['b'] = 2
  d['c'] = 3
  assert len(d) == 3
  d.clear()
  assert len(d) == 0
  assert not 'a' in d
  assert not 'b' in d
  assert not 'c' in d


def test_popitem():
  d = OrderedDict()
  with pytest.raises(KeyError):
    assert d.popitem() == None
  d['a'] = 1
  d['b'] = 2
  d['c'] = 3
  assert ('c', 3) == d.popitem()
  assert ('a', 1) == d.popitem(last=False)
  assert ('b', 2) == d.popitem()
  with pytest.raises(KeyError):
    assert d.popitem() == None


def test_update_dict():
  d = OrderedDict()
  # Update with a dict object
  other = {'a': 1, 'b': 2}
  d.update(other)
  assert len(d) == 2
  assert d['a'] == 1
  assert d['b'] == 2


def test_update_keys_ducktype():
  # Update If E has a .keys() method, for k in E.keys(): od[k] = E[k]
  class Quack(object):
    """A simple class that keeps keys/values in order
    """

    def __init__(self):
      self._store = []

    def keys(self):
      return [x[0] for x in self._store]

    def __setitem__(self, key, value):
      # A very stupid implementation, will always append a new item even if it is a dup
      self._store.append((key, value))

    def __getitem__(self, key):
      for x in self._store:
        if x[0] == key:
          return x[1]

  duck = Quack()
  duck['a'] = 1
  duck['b'] = 2
  duck['c'] = 3
  d = OrderedDict()
  d.update(duck)
  assert d.keys() == ['a', 'b', 'c']
  assert d.values() == [1, 2, 3]


def test_update_keys_iterable():
  items = [('a', 1), ('b', 2), ('c', 3)]
  d = OrderedDict()
  d.update(items)
  assert d.keys() == ['a', 'b', 'c']
  assert d.values() == [1, 2, 3]


def test_update_kwds():
  d = OrderedDict()
  items = [('a', 1)]
  d.update(items, b=2, c=3)
  assert d.keys()[0] == 'a'
  assert d.values()[0] == 1
  # The kwds arguments are not necessarily parsed in order
  assert set(d.keys()) == set(['a', 'b', 'c'])
  assert set(d.values()) == set([1, 2, 3])


def test_copy():
  d = OrderedDict()
  d['a'] = 1
  d['b'] = 2
  d['c'] = 3
  copy = d.copy()
  assert copy == d
  d['a'] = 99
  d['z'] = 100
  assert copy.keys() == ['a', 'b', 'c']
  assert copy.values() == [1, 2, 3]

  # Test that the copy is shallow
  d = OrderedDict()
  values = ['c', 'o', 'p', 'y']
  d['a'] = values
  copy = d.copy()
  assert copy == d
  values[0] = 'C'
  assert d['a'] == ['C', 'o', 'p', 'y']


def test_fromkeys():
  keys = ['a', 'b', 'c']
  d = OrderedDict.fromkeys(keys)
  assert d.keys() == ['a', 'b', 'c']
  assert d.values() == [None, None, None]
  d = OrderedDict.fromkeys(keys, value='foo')
  assert d.keys() == ['a', 'b', 'c']
  assert d.values() == ['foo', 'foo', 'foo']


def test_eq_neq():
  d1 = OrderedDict()
  d2 = OrderedDict()
  d1['a'] = d2['a'] = 1
  d1['b'] = d2['b'] = 2
  d1['c'] = d2['c'] = 3
  assert d1 == d2
  assert d2 == d1
  d2['c'] = 4
  assert d1 != d2
  assert d1 != d2
  d2['c'] = 3
  assert d1 == d2
  assert d2 == d1
  d2['d'] = 4
  assert d1 != d2
  assert d2 != d1
  d2 = OrderedDict()
  d2['b'] = 2
  d2['c'] = 3
  d2['a'] = 1
  assert d1 != d2
  assert d2 != d1


def test_move_to_end():
  d = OrderedDict()
  d['a'] = 1
  d['b'] = 2
  d['c'] = 3
  assert d.keys() == ['a', 'b', 'c']
  assert d.values() == [1, 2, 3]
  d.move_to_end('a')
  assert d.keys() == ['b', 'c', 'a']
  assert d.values() == [2, 3, 1]
  d.move_to_end('a', last=False)
  assert d.keys() == ['a', 'b', 'c']
  assert d.values() == [1, 2, 3]
