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

from .java_types import *
from .attribute_info import Attribute
from .signature_parser import BaseType
from . import util

_UNPARSED = (None, 0)

class FieldInfoFlags(object):
  """http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#88358
  """

  ACC_PUBLIC	 = 0x0001
  ACC_PRIVATE	 = 0x0002
  ACC_PROTECTED	 = 0x0004
  ACC_STATIC	 = 0x0008
  ACC_FINAL	 = 0x0010
  ACC_VOLATILE	 = 0x0040
  ACC_TRANSIENT	 = 0x0080

  def __init__(self, data):
    self._flags = u2(data).get()

  def public(self):
    return self._flags & FieldInfoFlags.ACC_PUBLIC

  def private(self):
    return self._flags & FieldInfoFlags.ACC_PRIVATE

  def protected(self):
    return self._flags & FieldInfoFlags.ACC_PROTECTED

  def static(self):
    return self._flags & FieldInfoFlags.ACC_STATIC

  def final(self):
    return self._flags & FieldInfoFlags.ACC_FINAL

  def volatile(self):
    return self._flags & FieldInfoFlags.ACC_VOLATILE

  def transient(self):
    return self._flags & FieldInfoFlags.ACC_TRANSIENT

  def __str__(self):
    verbs = []
    if self.public(): verbs.append('public')
    if self.private(): verbs.append('private')
    if self.protected(): verbs.append('protected')
    if self.static(): verbs.append('static')
    if self.final(): verbs.append('final')
    if self.volatile(): verbs.append('volatile')
    if self.transient(): verbs.append('transient')
    return ' '.join(verbs)

class ObjectType(object):
  @staticmethod
  def match(data):
    if data[0] == 'L':
      eof = data.find(';')
      return data[1:eof], eof + 1
    else:
      return _UNPARSED

class ArrayType(object):
  @staticmethod
  def match(data):
    if data[0] == '[':
      component, offset = ComponentType.match(data[1:])
      return component+'[]', offset + 1
    else:
      return _UNPARSED

class ComponentType(object):
  @staticmethod
  def match(data):
    return FieldType.match(data)

class FieldDescriptor(object):
  @staticmethod
  def match(data):
    return FieldType.match(data)

class FieldType(object):
  """http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1170

    FieldType:
      BaseType
      ObjectType
      ArrayType

    FieldDescriptor:
      FieldType

    ComponentType:
      FieldType

    BaseType: 'B' | 'C' | 'D' | 'F' | 'I' | 'J' | 'S' | 'Z'

    ObjectType:
      L <classname> ;

    ArrayType:
      [ ComponentType
  """
  @staticmethod
  def match(data):
    base_type, offset = BaseType.match(data)
    if offset: return base_type, offset
    object_type, offset = ObjectType.match(data)
    if offset: return object_type, offset
    array_type, offset = ArrayType.match(data)
    if offset: return array_type, offset
    return _UNPARSED

class FieldInfo(object):
  def __init__(self, data, constants):
    self._access_flags = FieldInfoFlags(data[0:2])
    (self._name_index, self._descriptor_index, self._attributes_count), data = \
      JavaNativeType.parse(data[2:], u2, u2, u2)
    self._name = constants[self._name_index] # synthesized
    self._descriptor = constants[self._descriptor_index] # synthesized
    self._parsed_descriptor, _ = FieldDescriptor.match(self._descriptor.bytes())
    self._attributes = []
    offset = 0
    for k in range(self._attributes_count):
      attribute = Attribute.parse(data[offset:], constants)
      offset += attribute.size()
      self._attributes.append(attribute)
    self._size = offset + 8

  def size(self):
    return self._size

  def __str__(self):
    base = '%s %s %s' % (
      self._access_flags,
      util.javaify(self._parsed_descriptor),
      self._name)
    if self._attributes:
      for attr in self._attributes:
        base += '\n    %s: %s' % (attr.name(), attr)
      base += '\n'
    return base
