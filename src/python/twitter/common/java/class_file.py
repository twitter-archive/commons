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
from .class_flags import ClassFlags
from .constant import (
  Constant, LongConstant, DoubleConstant, ClassConstant,
  FieldrefConstant, InterfaceMethodrefConstant, MethodrefConstant)
from .field_info import FieldInfo
from .method_info import MethodInfo
from .attribute_info import Attribute
from .signature_parser import PackageSpecifier

from hashlib import md5

class ClassDecoders:
  @staticmethod
  def decode_constant_pool(data, count):
    # 0th entry is a sentinel, since constants are indexed 1..constant_pool_size
    constants = [None]
    offset = 0
    skip = False
    for k in range(1, count):
      if skip:
        skip = False
        continue
      constant = Constant.parse(data[offset:])
      constants.append(constant)
      offset += constant.size()
      # special cases for Long/Double constants!
      # http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1348
      if isinstance(constant, (LongConstant, DoubleConstant)):
        constants.append(None)  # append a sentinel
        skip = True
    return constants, data[offset:]

  @staticmethod
  def decode_interfaces(data, count, constants):
    interfaces, data = JavaNativeType.parse(data, *[u2]*count)
    interfaces = map(lambda offset: constants[offset], interfaces)
    return interfaces, data

  @staticmethod
  def decode_fields(data, count, constants):
    fields = []
    offset = 0
    for k in range(count):
      field = FieldInfo(data[offset:], constants)
      offset += field.size()
      fields.append(field)
    return fields, data[offset:]

  @staticmethod
  def decode_methods(data, count, constants):
    methods = []
    offset = 0
    for k in range(count):
      method = MethodInfo(data[offset:], constants)
      offset += method.size()
      methods.append(method)
    return methods, data[offset:]

  @staticmethod
  def decode_attributes(data, count, constants):
    attributes = []
    offset = 0
    for k in range(count):
      attribute = Attribute.parse(data[offset:], constants)
      offset += attribute.size()
      attributes.append(attribute)
    return attributes, data[offset:]

class ClassFile(object):
  """Wrapper for a .class file.
  """

  _LINKAGE_CONSTANT_TYPES = (
    FieldrefConstant,
    InterfaceMethodrefConstant,
    MethodrefConstant)

  def __init__(self, data):
    self._data = data
    self._decode()
    self._track_dependencies()

  def _linkage_constants(self):
    return [
      c for c in self._constant_pool
      if isinstance(c, self._LINKAGE_CONSTANT_TYPES)]

  def linkage_signature(self):
    cs = [c(self._constant_pool) for c in self._linkage_constants()]
    m = md5()
    m.update('\n'.join(sorted(cs)))
    return m.hexdigest()

  def _track_dependencies(self):
    self._external_references = set(
      c(self._constant_pool) for c in self._linkage_constants())

  @staticmethod
  def from_fp(fp):
    return ClassFile(fp.read())

  @staticmethod
  def from_file(filename):
    with open(filename, 'rb') as fp:
      return ClassFile.from_fp(fp)

  def _decode(self):
    data = self._data

    (self._magic, self._minor_version, self._major_version), data = \
      JavaNativeType.parse(data, u4, u2, u2)
    assert self._magic == 0xCAFEBABE

    # constant pool
    (self._constant_pool_count,), data = JavaNativeType.parse(data, u2)
    self._constant_pool, data = ClassDecoders.decode_constant_pool(
      data, self._constant_pool_count)

    (access_flags, this_class, super_class), data = JavaNativeType.parse(data, u2, u2, u2)
    self._access_flags  = ClassFlags(access_flags)
    self._this_class    = self._constant_pool[this_class]
    self._super_class   = self._constant_pool[super_class]

    # interfaces
    (self._interfaces_count,), data = JavaNativeType.parse(data, u2)
    self._interfaces, data = ClassDecoders.decode_interfaces(
      data, self._interfaces_count, self._constant_pool)

    # fields
    (self._fields_count,), data = JavaNativeType.parse(data, u2)
    self._fields, data = ClassDecoders.decode_fields(
      data, self._fields_count, self._constant_pool)

    # methods
    (self._methods_count,), data = JavaNativeType.parse(data, u2)
    self._methods, data = ClassDecoders.decode_methods(
      data, self._methods_count, self._constant_pool)

    # attributes
    (self._attributes_count,), data = JavaNativeType.parse(data, u2)
    self._attributes, data = ClassDecoders.decode_attributes(
      data, self._attributes_count, self._constant_pool)

  def version(self):
    return self._major_version, self._minor_version

  def methods(self):
    return self._methods

  def attributes(self):
    return self._attributes

  def interfaces(self):
    return self._interfaces

  def fields(self):
    return self._fields

  def this_class(self):
    return self._this_class(self._constant_pool)

  def super_class(self):
    return self._super_class(self._constant_pool)

  def constants(self):
    return self._constant_pool

  def constant(self, index):
    return self._constant_pool[index](self._constant_pool)

  def access_flags(self):
    return self._access_flags

  def __str__(self):
    const = self._constant_pool
    output = []
    output.append("class version: (%d, %d)" % (self._major_version, self._minor_version))
    output.append("this: %s" % self._this_class(const))
    output.append("super: %s" % self._super_class(const))
    output.append("access flags: %s" % self._access_flags)
    if self._interfaces:
      output.append("interfaces: ")
      for interface in self._interfaces:
        output.append("  %s" % interface(const))
    if self._fields:
      output.append("fields: ")
      for field in self._fields:
        output.append("  %s" % field)
    if self._methods:
      output.append("methods: ")
      for method in self._methods:
        output.append("  %s" % method)
    if self._attributes:
      output.append("attributes: ")
      for attribute in self._attributes:
        output.append("  %s" % attribute)
    if self._external_references:
      output.append("external references: ")
      for ref in self._external_references:
        output.append("  %s" % ref)
    output.append("linkage signature: \n  %s" % self.linkage_signature())
    return '\n'.join(output)
