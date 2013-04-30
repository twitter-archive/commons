# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

class RingBuffer(list):
  """List-based, capped-length circular buffer container.

  While this behaves similarly to a collections.deque(maxlen), the chief
  advantage is O(1) characteristics for random access.

    >>> from twitter.common.collections import RingBuffer
    >>> rr = RingBuffer(5)
    >>> rr
    RingBuffer([], size=5)
    >>> for i in xrange(0, 5):
    ...   rr.append(i)
    ...
    >>> rr
    RingBuffer([0, 1, 2, 3, 4], size=5)
    >>> for i in xrange(5, 8):
    ...   rr.append(i)
    ...
    >>> rr
    RingBuffer([3, 4, 5, 6, 7], size=5)
    >>> rr[1]
    4
    >>> rr[-2]
    6

  """

  __slots__ = ('_zero', '_size', '_count')

  class InvalidOperation(Exception): pass

  def __init__(self, size=1, iv=None):
    if not isinstance(size, int) or not size >= 1:
      raise ValueError('Size must be an integer >= 1')
    if iv is not None:
      super(RingBuffer, self).__init__([iv] * size)
      self._count = size
    else:
      self._count = 0
    self._zero = 0
    self._size = size

  def __index(self, key):
    if not self._count:
      raise IndexError('list index out of range')
    return (key + self._zero) % self._count

  def append(self, value):
    if self._count < self._size:
      super(RingBuffer, self).append(value)
      self._count += 1
    else:
      super(RingBuffer, self).__setitem__(self._zero % self._size, value)
      self._zero += 1

  def __getitem__(self, key):
    return super(RingBuffer, self).__getitem__(self.__index(key))

  def __setitem__(self, key, value):
    return super(RingBuffer, self).__setitem__(self.__index(key), value)

  def __delitem__(self, key):
    raise self.InvalidOperation('Cannot delete in a RingBuffer.')

  def __str__(self):
    return str(list(iter(self)))

  def __repr__(self):
    return "RingBuffer(size=%s, %s)" % (self._size, str(self))

  def __iter__(self):
    for x in xrange(0, self._count):
      yield self[x]
