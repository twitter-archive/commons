# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

from collections import Mapping
import struct
import threading

from .attribute_buffer import AttributeBuffer
from .builders.perfdata2 import PerfData2Format


class PerfDataMapping(Mapping):
  class Error(Exception): pass
  class ParseError(Error): pass

  def __init__(self, hsperf_provider, hsperf_builder):
    self._provider = hsperf_provider
    self._builder = hsperf_builder
    self._map = {}
    self._lock = threading.RLock()

  def __iter__(self):
    with self._lock:
      return iter(self._map)

  def __getitem__(self, key):
    with self._lock:
      return self._map[key]

  def __len__(self):
    with self._lock:
      return len(self._map)

  def sample(self):
    with self._lock:
      self._buffer = self._provider()
      try:
        new_map = self._builder(self._buffer)
        if new_map:
          self._map = new_map
      except ValueError as e:
        raise self.ParseError(str(e))


class PerfData(object):
  """A delegation class for picking the proper PerfData implementation.

     Ex:

       def provider(filename):
         with open(filename, 'rb') as fp:
           return fp.read()
       pd = PerfData.get(partial(provider, '/tmp/hsperfdata_root/12345'))
  """
  MAGIC = '\xca\xfe\xc0\xc0'

  @classmethod
  def _parse_prologue(cls, hsperf):
    """Read the hsperf data buffer prologue:
       4-byte magic: 0xcafec0c0
       1-byte: byte order (big_endian == 0, little_endian == 1)
       1-byte: major
       1-byte: minor
       1-byte: reserved

       Returns tuple of (major, minor, endianness).
    """
    if hsperf[0:4] != cls.MAGIC:
      raise ValueError('Buffer does not appear to be hsperf')
    try:
      endianness, major, minor = struct.unpack('BBB', hsperf[4:7])
    except struct.error as e:
      raise ValueError('Failed to unpack hsperfdata header: %s' % e)
    endianness = AttributeBuffer.BIG_ENDIAN if endianness == 0 else AttributeBuffer.LITTLE_ENDIAN
    return (major, minor, endianness)

  @classmethod
  def get(cls, provider):
    hsperf = provider()
    major, minor, endianness = cls._parse_prologue(hsperf)

    if major == 2 and minor == 0:
      return PerfDataMapping(provider, PerfData2Format(endianness))

    raise ValueError('Unknown hsperf format: only support 2.0 perf data buffers.')
