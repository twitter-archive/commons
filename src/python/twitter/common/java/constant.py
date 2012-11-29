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
import re

"""
  Parse constants as defined in
  http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#20080
"""

class ConstantBase(object):
  def __call__(self, constants):
    return 'AnonymousConstant()'

  def parse(self, data):
    elements, _ = JavaNativeType.parse(data, *self.__class__.TYPES)
    return elements

  def size(self):
    return sum(map(lambda typ: typ.size(), self.__class__.TYPES))

  def get_external_class_references(self, constants):
    """
    Retrieve all of the classes referenced by this class.

    Parameters:
      constants: the constant pool that this constant is a member of.
    Returns: a set of all classes referenced by this pool entry.
    """
    return set([])

class ClassConstant(ConstantBase):
  """
    u1 tag
    u2 name_index
  """
  TYPES = [u1, u2]

  def __init__(self, data):
    self._tag = u1(data[0:1]).get()
    self._name_index = u2(data[1:3]).get()

  def __call__(self, constants):
    return str(constants[self._name_index].bytes())

  def get_external_class_references(self, constants):
    classname = self(constants)
    if classname.startswith("["):
      matches = ClassPatternRE.findall(classname)
      if matches is None:
        return set()
      else:
        return set(matches)
    else:
      return set([self(constants)])

class FieldrefConstant(ConstantBase):
  """
    u1 tag
    u2 class_index
    u2 name_and_type_index
  """

  TYPES = [u1, u2, u2]

  def __init__(self, data):
    self._tag, self._class_index, self._name_and_type_index = self.parse(data)

  def __call__(self, constants):
    return '%s.%s' % (
      constants[self._class_index](constants),
      constants[self._name_and_type_index](constants))

  def get_external_class_references(self, constants):
    return constants[self._class_index].get_external_class_references(constants)

class MethodrefConstant(ConstantBase):
  """
    u1 tag
    u2 class_index
    u2 name_and_type_index
  """

  TYPES = [u1, u2, u2]

  def __init__(self, data):
    self._tag, self._class_index, self._name_and_type_index = self.parse(data)

  def __call__(self, constants):
    return '%s.%s' % (
      constants[self._class_index](constants),
      constants[self._name_and_type_index](constants))

  def get_external_class_references(self, constants):
    return (constants[self._class_index].get_external_class_references(constants).union(
            constants[self._name_and_type_index].get_external_class_references(constants)))


class InterfaceMethodrefConstant(ConstantBase):
  """
    u1 tag
    u2 class_index
    u2 name_and_type_index
  """

  TYPES = [u1, u2, u2]

  def __init__(self, data):
    self._tag, self._class_index, self._name_and_type_index = self.parse(data)

  def __call__(self, constants):
    return '%s.%s' % (
      constants[self._class_index](constants),
      constants[self._name_and_type_index](constants))

  def get_external_class_references(self, constants):
    return (constants[self._class_index].get_external_class_references(constants).union(
            constants[self._name_and_type_index].get_external_class_references(constants)))

class StringConstant(ConstantBase):
  """
    u1 tag
    u2 string_index
  """
  TYPES = [u1, u2]

  def __init__(self, data):
    self._tag, self._string_index = self.parse(data)

class IntegerConstant(ConstantBase):
  """
    u1 tag
    u4 bytes
  """
  TYPES = [u1, u4]

  def __init__(self, data):
    self._tag, self._bytes = self.parse(data)

class FloatConstant(ConstantBase):
  """
    u1 tag
    u4 bytes
  """
  TYPES = [u1, u4]

  def __init__(self, data):
    self._tag, self._bytes = self.parse(data)

class LongConstant(ConstantBase):
  """
    u1 tag
    u4 high_bytes
    u4 low_bytes
  """
  TYPES = [u1, u4, u4]

  def __init__(self, data):
    self._tag, self._high_bytes, self._low_bytes = self.parse(data)

class DoubleConstant(ConstantBase):
  """
    u1 tag
    u4 high_bytes
    u4 low_bytes
  """
  TYPES = [u1, u4, u4]

  def __init__(self, data):
    self._tag, self._high_bytes, self._low_bytes = self.parse(data)

class NameAndTypeConstant(ConstantBase):
  """
    u1 tag
    u2 name_index
    u2 descriptor_index
  """
  TYPES = [u1, u2, u2]

  def __init__(self, data):
    self._tag, self._name_index, self._descriptor_index = self.parse(data)

  def size(self):
    return u1.size() + u2.size() + u2.size()

  def __call__(self, constants):
    return '%s.%s' % (
      constants[self._name_index].bytes(),
      constants[self._descriptor_index].bytes())

  # Here, we unfortunately need to get a bit fuzzy.
  # We need to extract type identifiers from the descriptor string.
  # With some of the tricks that languages like Scala play with class
  # names, that can get tricky to get right.
  def get_external_class_references(self, constants):
    matches = ClassPatternRE.findall(constants[self._descriptor_index].bytes())
    if matches is None:
      return set([])
    else:
      return set(matches)

class Utf8Constant(ConstantBase):
  """
    u1 tag
    u2 length
    u1 bytes[length]
  """
  def __init__(self, data):
    (self._tag, self._length), data = JavaNativeType.parse(data, u1, u2)
    self._bytes = data[0:self._length]

  def size(self):
    return u1.size() + u2.size() + self._length

  def bytes(self):
    return self._bytes

  def __str__(self):
    return self._bytes

class Constant(object):
  # http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1221
  CONSTANT_Class              = 7
  CONSTANT_Fieldref           = 9
  CONSTANT_Methodref          = 10
  CONSTANT_InterfaceMethodref = 11
  CONSTANT_String             = 8
  CONSTANT_Integer            = 3
  CONSTANT_Float              = 4
  CONSTANT_Long               = 5
  CONSTANT_Double             = 6
  CONSTANT_NameAndType        = 12
  CONSTANT_Utf8               = 1

  _BASE_TYPES = {
    CONSTANT_Class: ClassConstant,
    CONSTANT_Fieldref: FieldrefConstant,
    CONSTANT_Methodref: MethodrefConstant,
    CONSTANT_InterfaceMethodref: InterfaceMethodrefConstant,
    CONSTANT_String: StringConstant,
    CONSTANT_Integer: IntegerConstant,
    CONSTANT_Float: FloatConstant,
    CONSTANT_Long: LongConstant,
    CONSTANT_Double: DoubleConstant,
    CONSTANT_NameAndType: NameAndTypeConstant,
    CONSTANT_Utf8: Utf8Constant
  }

  @staticmethod
  def parse(data):
    tag = u1(data[0]).get()
    constant = Constant._BASE_TYPES[tag](data)
    return constant


# A regular expression matcher for recognizing class references
# in Java type signature strings. This is for use in ClassConstant and NameAndTypeConstant.
#
# The class file format for a class reference is a capital L,
# followed by the class name in path format, terminated by semicolon.
#
# Type signatures appear in two places:
# - in a method reference, describing the type signature of the method.
# - in a class reference to an array type (e.g., "]Ljava/lang/Object;" for
#    an array of object.)
#
# Parametric types cause some trouble here. A parametric can appear
# as LTypeA<LTypeB;>;.
#
# We cheat a bit here, and say that L introduces a type, and it's
# ended by either <, >, or ;.
#
# So if we saw a string (like this actual one)
# "Lscala/Option<Lcom/foursquare/auth/AuthorizableUser;>;"
# our regex would match "Lscala/Option<", and "Lcom/foursquare/auth/AuthorizableUser;"
# as referenced class names.
ClassPatternRE = re.compile("L([A-Za-z_0-9/$]*)[<>;]")
