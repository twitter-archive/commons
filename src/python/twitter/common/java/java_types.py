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

import struct

class JavaNativeType(object):
  class ParseException(Exception): pass

  def __init__(self, data):
    pass

  def __call__(self):
    return self._value

  def value(self):
    return self._value

  def get(self):
    return self.value()

  @staticmethod
  def size():
    raise Exception("Unimplemented!")

  @staticmethod
  def parse(data, *type_args):
    offset = 0
    parsed_types = []
    total_size = 0
    for t in type_args:
      if not issubclass(t, JavaNativeType):
        raise JavaNativeType.ParseException("Not a valid JavaNativeType: %s" % t)
      total_size += t.size()
    if total_size > len(data):
      raise JavaNativeType.ParseException("Not enough data to deserialize %s" % repr(type_args))
    for t in type_args:
      parsed_type = t(data[slice(offset, offset + t.size())]).value()
      parsed_types.append(parsed_type)
      offset += t.size()
    return parsed_types, data[total_size:]

class u1(JavaNativeType):
  def __init__(self, data):
    JavaNativeType.__init__(self, data)
    self._value = struct.unpack('>B', data[0:1])[0]

  @staticmethod
  def size():
    return 1

class u2(JavaNativeType):
  def __init__(self, data):
    JavaNativeType.__init__(self, data)
    self._value = struct.unpack(">H", data[0:2])[0]

  @staticmethod
  def size():
    return 2

class s2(JavaNativeType):
  def __init__(self, data):
    JavaNativeType.__init__(self, data)
    self._value = struct.unpack(">h", data[0:2])[0]

  @staticmethod
  def size():
    return 2

class u4(JavaNativeType):
  def __init__(self, data):
    JavaNativeType.__init__(self, data)
    self._value = struct.unpack(">L", data[0:4])[0]

  @staticmethod
  def size():
    return 4

class s4(JavaNativeType):
  def __init__(self, data):
    JavaNativeType.__init__(self, data)
    self._value = struct.unpack(">l", data[0:4])[0]

  @staticmethod
  def size():
    return 4

class s8(JavaNativeType):
  def __init__(self, data):
    JavaNativeType.__init__(self, data)
    self._value = struct.unpack(">q", data[0:8])[0]

  @staticmethod
  def size():
    return 8

class f4(JavaNativeType):
  def __init__(self, data):
    JavaNativeType.__init__(self, data)
    self._value = struct.unpack(">f", data[0:4])[0]

  @staticmethod
  def size():
    return 4

class f8(JavaNativeType):
  def __init__(self, data):
    JavaNativeType.__init__(self, data)
    self._value = struct.unpack(">d", data[0:8])[0]

  @staticmethod
  def size():
    return 8
