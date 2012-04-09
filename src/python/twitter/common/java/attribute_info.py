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

import sys
from .java_types import *
from .class_flags import ClassFlags
from . import signature_parser

class AttributeInfo(object):
  """
    Encapsulate the attribute_info class.
    http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#43817

    attribute_info {
      u2 attribute_name_index;
      u4 attribute_length;
      u1 info[attribute_length];
    }
  """
  def __init__(self, data, constants):
    self._parse_header(data, constants)

  def _parse_header(self, data, constants):
    self._attribute_name_index = u2(data[0:2]).get()
    self._attribute_name       = constants[self._attribute_name_index]
    self._attribute_length     = u4(data[2:6]).get()
    self._size                 = 6 + self._attribute_length
    self._info_data            = data[6:self._size]

  def name(self):
    return self._attribute_name

  def size(self):
    """Total size of the attribute_info blob."""
    return self._size

  def parsed_name(self):
    return self._attribute_name

  def bytes(self):
    """Attribute-specific data for subclasses."""
    return self._info_data

  def __str__(self):
    return 'AttributeInfo(name:%s, size=%d)' % (self._attribute_name, self.size())

class Code(AttributeInfo):
  """
    Code_attribute {
      u2 attribute_name_index;
      u4 attribute_length;
      u2 max_stack;
      u2 max_locals;
      u4 code_length;
      u1 code[code_length];
      u2 exception_table_length;
      {
        u2 start_pc;
        u2 end_pc;
        u2 handler_pc;
        u2 catch_type;
     } exception_table[exception_table_length];
     u2 attributes_count;
     attribute_info attributes[attributes_count];
  }
  """
  @staticmethod
  def name():
    return 'Code'

  def __init__(self, data, constants):
    AttributeInfo.__init__(self, data, constants)
    bytes = self.bytes()

    (max_stack, max_locals, code_length), bytes = JavaNativeType.parse(bytes, u2, u2, u4)
    self._code_length = code_length

    bytecode = bytes[0:code_length]
    bytes = bytes[code_length:]
    (exception_table_length,), bytes = JavaNativeType.parse(bytes, u2)

    # gobble up stuff
    for k in range(exception_table_length):
      _, bytes = JavaNativeType.parse(bytes, u2, u2, u2, u2)

    (attributes_count,), bytes = JavaNativeType.parse(bytes, u2)
    attributes = []
    offset = 0
    for k in range(attributes_count):
      attribute = Attribute.parse(bytes[offset:], constants)
      offset += attribute.size()
      attributes.append(attribute)
    self._attributes = attributes

  def __str__(self):
    output = 'Code(length:%s)' % self._code_length
    if self._attributes:
      output += '\n'
      attrs = []
      for attr in self._attributes:
        attrs.append('        %s: %s' % (attr.name(), attr))
      output += '\n'.join(attrs)
    return output

class SourceFile(AttributeInfo):
  """
    http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#79868
    SourceFile_attribute {
      u2 attribute_name_index;
      u4 attribute_length;
      u2 sourcefile_index;
    }
  """
  @staticmethod
  def name():
    return 'SourceFile'

  def __init__(self, data, constants):
    AttributeInfo.__init__(self, data, constants)
    bytes = self.bytes()
    self._sourcefile_index     = u2(bytes[0:2]).get()
    self._sourcefile           = constants[self._sourcefile_index]

  def __str__(self):
    return 'SourceFile(file:%s)' % self._sourcefile

class Exceptions(AttributeInfo):
  """
    http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#3129
    Exceptions_attribute {
      u2 attribute_name_index;
      u4 attribute_length;
      u2 number_of_exceptions;
      u2 exception_index_table[number_of_exceptions];
    }
  """
  @staticmethod
  def name():
    return 'Exceptions'

  def __init__(self, data, constants):
    AttributeInfo.__init__(self, data, constants)
    bytes = self.bytes()

    self._number_of_exceptions = u2(bytes[0:2]).get()
    self._exceptions = []
    for index in range(self._number_of_exceptions):
      constant_index = u2(bytes[2*(index+1):]).get()
      self._exceptions.append(constants[constant_index](constants))

  def __str__(self):
    if self._exceptions:
      return 'throws %s' % ' '.join('%s' % s for s in self._exceptions)
    else:
      return ''

class Signature(AttributeInfo):
  """
    Signature_attribute {
      u2 attribute_name_index;
      u4 attribute_length;
      u2 signature_index
    }
  """
  @staticmethod
  def name():
    return 'Signature'

  def __init__(self, data, constants):
    AttributeInfo.__init__(self, data, constants)
    bytes = self.bytes()

    self._signature_index = u2(bytes[0:2]).get()
    self._signature = constants[self._signature_index]
    self._parsed = None
    self._parse_signature()

  def _parse_signature(self):
    class_signature, _ = signature_parser.ClassSignature.match(self._signature.bytes())
    if class_signature:
      self._parsed = class_signature
      return

    method_signature, _ = signature_parser.MethodTypeSignature.match(self._signature.bytes())
    if method_signature:
      self._parsed = method_signature

  def __str__(self):
    return 'Signature(%s)' % (
      self._parsed)

class InnerClassFlags(object):
  """http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#75734
  """
  ACC_PUBLIC	 = 0x0001
  ACC_PRIVATE	 = 0x0002
  ACC_PROTECTED	 = 0x0004
  ACC_STATIC	 = 0x0008
  ACC_FINAL	 = 0x0010
  ACC_INTERFACE	 = 0x0200
  ACC_ABSTRACT	 = 0x0400
  ACC_SYNTHETIC	 = 0x1000
  ACC_ANNOTATION = 0x2000
  ACC_ENUM	 = 0x4000

  MASK = ACC_PUBLIC   | ACC_PRIVATE   | ACC_PROTECTED  | \
         ACC_STATIC   | ACC_FINAL     | ACC_INTERFACE  | \
         ACC_ABSTRACT | ACC_SYNTHETIC | ACC_ANNOTATION | \
         ACC_ENUM

  def __init__(self, flags):
    self._flags = flags
    if flags ^ (flags & InnerClassFlags.MASK) != 0:
      print >> sys.stderr, "Invalid InnerClassFlags mask!! Extra bits: %s" % (
        flags ^ (flags & InnerClassFlags.MASK))

  def public(self):
    return self._flags & InnerClassFlags.ACC_PUBLIC

  def private(self):
    return self._flags & InnerClassFlags.ACC_PRIVATE

  def protected(self):
    return self._flags & InnerClassFlags.ACC_PROTECTED

  def static(self):
    return self._flags & InnerClassFlags.ACC_STATIC

  def final(self):
    return self._flags & InnerClassFlags.ACC_FINAL

  def interface(self):
    return self._flags & InnerClassFlags.ACC_INTERFACE

  def abstract(self):
    return self._flags & InnerClassFlags.ACC_ABSTRACT

  def synthetic(self):
    return self._flags & InnerClassFlags.ACC_SYNTHETIC

  def annotation(self):
    return self._flags & InnerClassFlags.ACC_ANNOTATION

  def enum(self):
    return self._flags & InnerClassFlags.ACC_ENUM

  def __str__(self):
    verbs = []
    if self.public(): verbs.append('public')
    if self.private(): verbs.append('private')
    if self.protected(): verbs.append('protected')
    if self.static(): verbs.append('static')
    if self.final(): verbs.append('final')
    if self.interface(): verbs.append('interface')
    if self.abstract(): verbs.append('abstract')
    if self.synthetic(): verbs.append('synthetic')
    if self.annotation(): verbs.append('annotation')
    if self.enum(): verbs.append('enum')
    return ' '.join(verbs)

class InnerClass(object):
  def __init__(self, data, constants):
    (inner_class_info_index, outer_class_info_index,
     inner_name_index, inner_class_flags), data = JavaNativeType.parse(data, u2, u2, u2, u2)

    debug = """
    print 'constant pool size, inner, outer, name, flags = %s, %s, %s, %s, %s => %s' % (
      len(constants),
      inner_class_info_index,
      outer_class_info_index,
      inner_name_index,
      inner_class_flags,
      InnerClassFlags(inner_class_flags))
    """

    self._inner_class = constants[inner_class_info_index]
    if outer_class_info_index < len(constants):
      self._outer_class = constants[outer_class_info_index]
    else:
      print >> sys.stderr, 'WARNING: Malformed InnerClass(outer_class_info_index)!'
      self._outer_class = None
    if inner_name_index < len(constants):
      self._inner_name = constants[inner_name_index]
    else:
      print >> sys.stderr, 'WARNING: Malformed InnerClass(inner_name)!'
      self._inner_name = None
    self._inner_class_access_flags = InnerClassFlags(inner_class_flags)

    if self._inner_class is not None:
      self._inner_class = self._inner_class(constants)
    if self._outer_class is not None:
      self._outer_class = self._outer_class(constants)
    if self._inner_name is not None:
      self._inner_name = self._inner_name(constants)
    else:
      self._inner_name = 'Anonymous'

  def __str__(self):
    return '%s %s::%s %s' % (
      self._inner_class_access_flags,
      self._outer_class,
      self._inner_class,
      self._inner_name)

class InnerClasses(AttributeInfo):
  """
    http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#79996
    InnerClasses_attribute {
      u2 attribute_name_index;
      u4 attribute_length;
      ------
      u2 number_of_classes;
      {  u2 inner_class_info_index;
         u2 outer_class_info_index;
         u2 inner_name_index;
         u2 inner_class_access_flags;
      } classes[number_of_classes];
    }
  """
  @staticmethod
  def name():
    return 'InnerClasses'

  def __init__(self, data, constants):
    AttributeInfo.__init__(self, data, constants)
    bytes = self.bytes()

    self._number_of_classes = u2(bytes[0:2]).get()
    self._classes = []
    offset = 2
    for index in range(self._number_of_classes):
      klass = InnerClass(data[offset:], constants)
      self._classes.append(klass)
      offset += 4 * u2.size()

  def __str__(self):
    return '{\n%s\n}' % ('\n  '.join('%s' % s for s in self._classes))

class Attribute(object):
  """
    Factory for producing AttributeInfos.
  """

  _KNOWN_ATTRIBUTE_MAP = {
    SourceFile.name(): SourceFile,
    Signature.name(): Signature,
    Exceptions.name(): Exceptions,
    Code.name(): Code
    # InnerClasses.name(): InnerClasses
  }

  @staticmethod
  def parse(data, constants):
    """Parse the Attribute_info

      @data: The data stream from which to deserialize the blob
      @constants: The constant pool of the class file.
    """
    attribute_name_index = u2(data[0:2]).get()
    attribute_name       = constants[attribute_name_index]

    attribute_class = Attribute._KNOWN_ATTRIBUTE_MAP.get(attribute_name.bytes(), None)
    if attribute_class is not None:
      return attribute_class(data, constants)
    else:
      return AttributeInfo(data, constants)
