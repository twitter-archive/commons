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

from abc import abstractproperty
import struct

from twitter.common.lang import Interface


class AttributeBuffer(Interface):
  """Provider a simple attribute proxy over a binary stream."""
  BIG_ENDIAN = 0
  LITTLE_ENDIAN = 1

  @abstractproperty
  def data(self):
    """Return the data stream of this attribute buffer."""

  @abstractproperty
  def attributes(self):
    """Return a map of attribute name => (struct format, byte range)"""

  @abstractproperty
  def endianness(self):
    """Return the endianness of the buffers."""

  def unpack(self, format_str, data_range):
    """Unpack a stream from the underlying buffer data_range using format_str."""
    new_format_str = ''.join(
        ('%c%c' % ('>' if self.endianness is self.BIG_ENDIAN else '<', format)
        for format in format_str))
    try:
      value = struct.unpack(new_format_str, self.data[data_range])
    except struct.error as e:
      raise ValueError('Possibly corrupt data buffer: %s' % e)
    if len(format_str) > 1:
      return value
    else:
      return value[0]

  def __getattr__(self, attribute):
    if attribute not in self.attributes:
      return self.__getattribute__(attribute)
    format_str, data_range = self.attributes[attribute]
    return self.unpack(format_str, data_range)


class SimpleAttributeBuffer(AttributeBuffer):
  ATTRIBUTES = {}

  def __init__(self, data, endianness):
    self._data, self._endianness = data, endianness

  @property
  def data(self):
    return self._data

  @property
  def endianness(self):
    return self._endianness

  @property
  def attributes(self):
    return self.ATTRIBUTES
