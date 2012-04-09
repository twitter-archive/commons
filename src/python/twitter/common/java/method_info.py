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

from .util import javaify
from .java_types import *
from .attribute_info import Attribute
from .field_info import FieldType

class MethodInfoFlags(object):
  """http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#75568
  """

  ACC_PUBLIC	   = 0x0001
  ACC_PRIVATE	   = 0x0002
  ACC_PROTECTED	   = 0x0004
  ACC_STATIC	   = 0x0008
  ACC_FINAL	   = 0x0010
  ACC_SYNCHRONIZED = 0x0020
  ACC_NATIVE       = 0x0100
  ACC_ABSTRACT     = 0x0400
  ACC_STRICT       = 0x0800

  def __init__(self, data):
    self._flags = u2(data).get()

  def public(self):
    return self._flags & MethodInfoFlags.ACC_PUBLIC

  def private(self):
    return self._flags & MethodInfoFlags.ACC_PRIVATE

  def protected(self):
    return self._flags & MethodInfoFlags.ACC_PROTECTED

  def static(self):
    return self._flags & MethodInfoFlags.ACC_STATIC

  def final(self):
    return self._flags & MethodInfoFlags.ACC_FINAL

  def synchronized(self):
    return self._flags & MethodInfoFlags.ACC_SYNCHRONIZED

  def native(self):
    return self._flags & MethodInfoFlags.ACC_NATIVE

  def abstract(self):
    return self._flags & MethodInfoFlags.ACC_ABSTRACT

  def strict(self):
    return self._flags & MethodInfoFlags.ACC_STRICT

  def __str__(self):
    verbs = []
    if self.public(): verbs.append('public')
    if self.private(): verbs.append('private')
    if self.protected(): verbs.append('protected')
    if self.static(): verbs.append('static')
    if self.final(): verbs.append('final')
    if self.synchronized(): verbs.append('synchronized')
    if self.native(): verbs.append('native')
    if self.abstract(): verbs.append('abstract')
    if self.strict(): verbs.append('strict')
    return ' '.join(verbs)

class MethodDescriptor(object):
  """http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1170

    MethodDescriptor:
      ( ParameterDescriptor* ) ReturnDescriptor

    ParameterDescriptor:
      FieldType

    ReturnDescriptor:
      FieldType
      V
  """

  @staticmethod
  def match(data):
    parameters = []
    assert data[0] == '('
    index = 1
    while True:
      descriptor, offset = ParameterDescriptor.match(data[index:])
      if offset == 0: break
      parameters.append(descriptor)
      index += offset
    assert data[index] == ')', 'data[%s] is actually: %s, full: %s' % (index, data[index], data)
    return_descriptor, offset = ReturnDescriptor.match(data[index+1:])
    return '%s %%s(%s)' % (
      javaify(return_descriptor),
      ', '.join(javaify(p) for p in parameters) if parameters else 'void'), index+1+offset

class ParameterDescriptor(object):
  @staticmethod
  def match(data):
    return FieldType.match(data)

class ReturnDescriptor(object):
  @staticmethod
  def match(data):
    field_type, offset = FieldType.match(data)
    if offset: return field_type, offset
    if data[0] == 'V': return 'void', 1
    return None, 0

class MethodInfo(object):
  def __init__(self, data, constants):
    self._access_flags      = MethodInfoFlags(data[0:2])
    (self._name_index, self._descriptor_index, self._attributes_count), data = \
      JavaNativeType.parse(data[2:], u2, u2, u2)
    self._name              = constants[self._name_index] # synthesized
    self._descriptor        = constants[self._descriptor_index] # synthesized
    self._parsed_descriptor, _ = MethodDescriptor.match(self._descriptor.bytes())
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
    output = []
    sig = ''
    if self._access_flags:
      sig += '%s ' % self._access_flags
    sig += (self._parsed_descriptor % self._name)
    output.append(sig)
    for attr in self._attributes:
      if attr.name() == 'Signature':
        output.append('    %s: %s' % (attr.name(), attr._parsed))
      else:
        output.append('    %s: %s' % (attr.name(), attr))
    return '\n'.join('%s' % o for o in output)
